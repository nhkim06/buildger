package com.example.backend.file.service;

import com.example.backend.construction.entity.Confirmation;
import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.construction.entity.FileEntry;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.construction.repository.RecordRepository;
import com.example.backend.construction.service.RecordAccessService;
import com.example.backend.construction.service.RecordContext;
import com.example.backend.file.dto.FileResponse;
import com.example.backend.file.entity.Storage;
import com.example.backend.file.repository.StorageRepository;
import com.example.backend.global.common.JsonbCodec;
import com.example.backend.global.common.Times;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.jsonb.Deletion;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.service.ProjectAccessService;
import com.example.backend.project.service.ProjectContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 이미지 업로드·조회·다운로드·삭제 (docs/api.md 11장)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final int MAX_FILES_PER_REQUEST = 10;

    private final ProjectAccessService projectAccess;
    private final RecordAccessService recordAccess;
    private final RecordRepository recordRepository;
    private final StorageRepository storageRepository;
    private final ImageFileValidator imageValidator;
    private final JsonbCodec jsonbCodec;
    private final PlatformTransactionManager transactionManager;

    /**
     * 1) 읽기 트랜잭션에서 권한·상태를 먼저 확인하고 (잘못된 요청에 대한 불필요한 이미지 처리 방지)
     * 2) 잠금 없이 이미지 검증·해시 계산을 한 뒤
     * 3) 쓰기 트랜잭션에서 project FOR SHARE → data FOR UPDATE 후 다시 검증하고 storage INSERT와
     *    metadata.files 갱신을 함께 수행한다. 실패하면 전체 롤백한다. (docs/api.md 11.1, 4.5)
     */
    public List<FileResponse> upload(Long projectId, Long dataId, AuthUser user, List<MultipartFile> files) {
        readOnlyTx().executeWithoutResult(status -> checkWritable(recordAccess.read(projectAccess.read(projectId, user), dataId)));

        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "한 번에 최대 10장까지 업로드할 수 있습니다.");
        }
        List<ValidatedImage> images = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        for (MultipartFile file : files) {
            ValidatedImage image = imageValidator.validate(file, ImagePolicy.PHOTO);
            if (!hashes.add(image.sha256())) {
                throw new BusinessException(ErrorCode.DUPLICATE_FILE, "같은 이미지가 요청에 중복되어 있습니다.");
            }
            images.add(image);
        }
        return new TransactionTemplate(transactionManager).execute(status -> store(projectId, dataId, user, images));
    }

    private List<FileResponse> store(Long projectId, Long dataId, AuthUser user, List<ValidatedImage> images) {
        ProjectContext ctx = projectAccess.share(projectId, user);
        RecordContext rc = recordAccess.lock(ctx, dataId);
        checkWritable(rc);

        RecordMetadata metadata = rc.metadata();
        int active = metadata.getActiveFiles().size();
        if (active + images.size() > RecordMetadata.MAX_ACTIVE_FILES
                || metadata.getFiles().size() + images.size() > RecordMetadata.MAX_TOTAL_FILES) {
            throw new BusinessException(ErrorCode.FILE_COUNT_EXCEEDED);
        }
        Set<String> existing = new HashSet<>();
        metadata.getActiveFiles().forEach(f -> existing.add(f.getSha256()));
        for (ValidatedImage image : images) {
            if (existing.contains(image.sha256())) {
                throw new BusinessException(ErrorCode.DUPLICATE_FILE);
            }
        }

        String now = Times.nowString();
        List<FileResponse> responses = new ArrayList<>();
        for (ValidatedImage image : images) {
            Storage storage = storageRepository.save(new Storage(image.originalName(), image.bytes()));
            FileEntry entry = new FileEntry();
            entry.setStorageId(storage.getStorageId());
            entry.setOriginalName(image.originalName());
            entry.setContentType(image.contentType());
            entry.setSize(image.size());
            entry.setSha256(image.sha256());
            entry.setWidth(image.width());
            entry.setHeight(image.height());
            entry.setUploadedBy(user.uid().toString());
            entry.setUploadedAt(now);
            metadata.getFiles().add(entry);
            responses.add(FileResponse.from(entry));
        }
        recordAccess.saveMetadata(rc.record(), metadata);
        return responses;
    }

    private void checkWritable(RecordContext rc) {
        recordAccess.requireModifiable(rc);
        recordAccess.requireNotInvalidationType(rc);
        recordAccess.requireNotLocked(rc);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> list(Long projectId, Long dataId, AuthUser user) {
        RecordContext rc = recordAccess.read(projectAccess.read(projectId, user), dataId);
        return rc.metadata().getActiveFiles().stream()
                .sorted(Comparator.comparing(f -> f.getUploadedAt() == null ? "" : f.getUploadedAt()))
                .map(FileResponse::from)
                .toList();
    }

    /**
     * storage와 data 사이에 FK가 없으므로 metadata에서 소속 기록을 찾아 프로젝트 권한을 검증한다.
     * 연결된 기록이 없거나 둘 이상이면(깨진 참조) 404로 응답한다. (docs/api.md 4.2, 11.3)
     */
    @Transactional(readOnly = true)
    public FileDownload download(Long storageId, AuthUser user) {
        String fileJson = jsonbCodec.write(List.of(Map.of("storageId", storageId)));
        String signatureJson = jsonbCodec.write(List.of(Map.of("signature", Map.of("storageId", storageId))));
        List<ConstructionRecord> records = recordRepository.findByStorageReference(fileJson, signatureJson);
        if (records.size() != 1) {
            if (records.size() > 1) {
                log.warn("Storage referenced by multiple records: storageId={}", storageId);
            }
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        ConstructionRecord record = records.get(0);
        RecordMetadata metadata = recordAccess.readMetadata(record);
        if (metadata.isDeleted()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        String contentType = null;
        String sha256 = null;
        FileEntry file = metadata.findActiveFile(storageId).orElse(null);
        if (file != null) {
            contentType = file.getContentType();
            sha256 = file.getSha256();
        } else {
            for (Confirmation confirmation : metadata.getConfirmations()) {
                if (confirmation.getSignature() != null && storageId.equals(confirmation.getSignature().getStorageId())) {
                    contentType = confirmation.getSignature().getContentType();
                    sha256 = confirmation.getSignature().getSha256();
                }
            }
        }
        if (contentType == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        try {
            projectAccess.read(record.getProjectId(), user);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PROJECT_NOT_FOUND) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
            }
            throw e;
        }
        Storage storage = storageRepository.findById(storageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        return new FileDownload(storage.getImage(), contentType, storage.getName(), sha256);
    }

    /** Soft Delete: files[] 항목에 deletion만 추가하고 storage 행은 유지한다. (docs/api.md 11.4) */
    @Transactional
    public void delete(Long projectId, Long dataId, Long storageId, AuthUser user) {
        ProjectContext ctx = projectAccess.share(projectId, user);
        RecordContext rc = recordAccess.lock(ctx, dataId);
        recordAccess.requireModifiable(rc);
        recordAccess.requireNotLocked(rc);
        FileEntry file = rc.metadata().findActiveFile(storageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        file.setDeletion(new Deletion(Times.nowString(), user.uid().toString(), null));
        recordAccess.saveMetadata(rc.record(), rc.metadata());
    }

    private TransactionTemplate readOnlyTx() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    public record FileDownload(byte[] bytes, String contentType, String fileName, String sha256) {
    }
}
