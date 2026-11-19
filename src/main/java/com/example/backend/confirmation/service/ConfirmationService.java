package com.example.backend.confirmation.service;

import com.example.backend.account.entity.Account;
import com.example.backend.account.entity.RoleLevel;
import com.example.backend.account.repository.AccountRepository;
import com.example.backend.confirmation.dto.ConfirmationRequest;
import com.example.backend.confirmation.dto.ConfirmationResponse;
import com.example.backend.construction.entity.Confirmation;
import com.example.backend.construction.entity.ConfirmationState;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.construction.entity.RecordType;
import com.example.backend.construction.service.RecordAccessService;
import com.example.backend.construction.service.RecordContext;
import com.example.backend.construction.service.RecordIntegrityVerifier;
import com.example.backend.construction.service.RecordSnapshotHasher;
import com.example.backend.construction.service.RecordStatusResolver;
import com.example.backend.construction.service.RecordStatusResolver.StatusInfo;
import com.example.backend.file.entity.Storage;
import com.example.backend.file.repository.StorageRepository;
import com.example.backend.file.service.ImageFileValidator;
import com.example.backend.file.service.ImagePolicy;
import com.example.backend.file.service.ValidatedImage;
import com.example.backend.construction.entity.SignatureEntry;
import com.example.backend.global.common.Times;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.service.ProjectAccessService;
import com.example.backend.project.service.ProjectContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 담당자 확인·서명 (docs/api.md 12장, 4.4).
 * 확인 항목은 추가 전용이며, 1건 이상이면 기록 본문은 변경할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class ConfirmationService {

    private final ProjectAccessService projectAccess;
    private final RecordAccessService recordAccess;
    private final RecordStatusResolver statusResolver;
    private final RecordSnapshotHasher snapshotHasher;
    private final RecordIntegrityVerifier integrityVerifier;
    private final ImageFileValidator imageValidator;
    private final StorageRepository storageRepository;
    private final AccountRepository accountRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 1) 읽기 트랜잭션에서 참여자·계정 역할을 먼저 확인한다.
     * 2) 잠금 없이 요청 값과 서명 이미지를 검증한다.
     * 3) 쓰기 트랜잭션에서 project FOR SHARE → data FOR UPDATE 후 상태·중복·이미지 해시를 다시 확인하고
     *    서명 이미지 저장과 confirmations 추가를 함께 수행한다.
     */
    public ConfirmationResponse confirm(Long projectId, Long dataId, AuthUser user, ConfirmationRequest request,
                                       MultipartFile signature) {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        readOnly.executeWithoutResult(status -> {
            ProjectContext ctx = projectAccess.read(projectId, user);
            requireConfirmerRole(user);
            recordAccess.read(ctx, dataId);
        });

        if (request.status() == ConfirmationState.NONE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "status는 APPROVED 또는 REJECTED여야 합니다.");
        }
        if (request.status() == ConfirmationState.REJECTED && (request.comment() == null || request.comment().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "REJECTED인 경우 의견(comment)은 필수입니다.");
        }
        ValidatedImage image = imageValidator.validate(signature, ImagePolicy.SIGNATURE);

        return new TransactionTemplate(transactionManager).execute(status -> store(projectId, dataId, user, request, image));
    }

    private ConfirmationResponse store(Long projectId, Long dataId, AuthUser user, ConfirmationRequest request,
                                       ValidatedImage image) {
        ProjectContext ctx = projectAccess.share(projectId, user);
        requireConfirmerRole(user);
        RecordContext rc = recordAccess.lock(ctx, dataId);
        RecordMetadata metadata = rc.metadata();

        if (rc.isAuthor()) {
            throw new BusinessException(ErrorCode.SELF_CONFIRMATION_NOT_ALLOWED);
        }
        StatusInfo status = statusResolver.resolve(dataId);
        if (status.invalidatedBy() != null) {
            throw new BusinessException(ErrorCode.RECORD_INVALIDATED);
        }
        if (status.supersededBy() != null) {
            throw new BusinessException(ErrorCode.RECORD_SUPERSEDED,
                    "정정된 이전 버전입니다. 최신 기록(#" + status.supersededBy() + ")에 서명하세요.");
        }
        if (rc.record().getRecordType() == RecordType.PHOTO && metadata.getActiveFiles().isEmpty()) {
            throw new BusinessException(ErrorCode.RECORD_HAS_NO_FILES);
        }
        if (metadata.getConfirmations().size() >= RecordMetadata.MAX_CONFIRMATIONS) {
            throw new BusinessException(ErrorCode.CONFIRMATION_LIMIT_EXCEEDED);
        }
        String uid = user.uid().toString();
        metadata.getConfirmations().stream()
                .filter(c -> uid.equals(c.getConfirmerUid()))
                .reduce((first, second) -> second)
                .filter(last -> last.getStatus() == request.status())
                .ifPresent(last -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_CONFIRMATION);
                });

        // 서명 대상 데이터의 무결성 확인 (docs/api.md 4.4 서명 시 1~2)
        if (!integrityVerifier.activeFilesMatch(metadata)) {
            throw new BusinessException(ErrorCode.INTEGRITY_MISMATCH, "첨부 이미지의 해시가 저장된 값과 일치하지 않습니다.");
        }
        String snapshotHash = snapshotHasher.hash(rc.record(), metadata);
        if (!integrityVerifier.snapshotMatches(metadata.getConfirmations(), snapshotHash)) {
            throw new BusinessException(ErrorCode.INTEGRITY_MISMATCH, "기존 서명 해시와 현재 기록 데이터가 일치하지 않습니다.");
        }

        Account confirmer = accountRepository.findById(user.uid())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        Storage storage = storageRepository.save(new Storage(image.originalName(), image.bytes()));

        SignatureEntry signature = new SignatureEntry();
        signature.setStorageId(storage.getStorageId());
        signature.setContentType(image.contentType());
        signature.setSize(image.size());
        signature.setSha256(image.sha256());

        Confirmation confirmation = new Confirmation();
        confirmation.setConfirmationId(UUID.randomUUID().toString());
        confirmation.setConfirmerUid(uid);
        confirmation.setConfirmerName(confirmer.getName());
        confirmation.setConfirmerOrganization(confirmer.getOrganization());
        confirmation.setConfirmerRoleLevel((int) confirmer.getRoleLevel());
        confirmation.setStatus(request.status());
        confirmation.setComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().strip());
        confirmation.setSignature(signature);
        confirmation.setSnapshotVersion(RecordSnapshotHasher.SNAPSHOT_VERSION);
        confirmation.setRecordSnapshotSha256(snapshotHash);
        confirmation.setConfirmedAt(Times.nowString());

        metadata.getConfirmations().add(confirmation);
        recordAccess.saveMetadata(rc.record(), metadata);
        return ConfirmationResponse.from(confirmation);
    }

    @Transactional(readOnly = true)
    public List<ConfirmationResponse> list(Long projectId, Long dataId, AuthUser user) {
        RecordContext rc = recordAccess.read(projectAccess.read(projectId, user), dataId);
        return rc.metadata().getConfirmations().stream()
                .sorted(Comparator.comparing(c -> c.getConfirmedAt() == null ? "" : c.getConfirmedAt()))
                .map(ConfirmationResponse::from)
                .toList();
    }

    private static void requireConfirmerRole(AuthUser user) {
        if (!RoleLevel.fromLevel(user.roleLevel()).isManagerial()) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED, "확인·서명은 시공사·시행사 계정만 할 수 있습니다.");
        }
    }
}
