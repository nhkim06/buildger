package com.example.backend.ai.service;

import com.example.backend.account.entity.Account;
import com.example.backend.account.repository.AccountRepository;
import com.example.backend.ai.client.AiAnalysisClient;
import com.example.backend.ai.client.AiAnalysisClient.AiImage;
import com.example.backend.ai.client.AiAnalysisClient.AiRequest;
import com.example.backend.ai.client.AiAnalysisClient.AiResult;
import com.example.backend.ai.client.AiClientException;
import com.example.backend.ai.dto.AiAnalysisRequest;
import com.example.backend.ai.dto.AiAnalysisResponse;
import com.example.backend.construction.entity.AiAnalysis;
import com.example.backend.construction.entity.AiAnalysisStatus;
import com.example.backend.construction.entity.FileEntry;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.construction.service.RecordAccessService;
import com.example.backend.construction.service.RecordContext;
import com.example.backend.construction.service.RecordStatusResolver;
import com.example.backend.file.entity.Storage;
import com.example.backend.file.repository.StorageRepository;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 분석 요청·결과 저장·조회 (docs/api.md 13장).
 * DB 잠금을 쥔 채 외부 호출을 하지 않도록 ①사전 검증(읽기) ②AI 호출(트랜잭션 밖) ③결과 저장(쓰기)으로 분리한다.
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private static final int MAX_TARGETS = 5;

    private final ProjectAccessService projectAccess;
    private final RecordAccessService recordAccess;
    private final RecordStatusResolver statusResolver;
    private final StorageRepository storageRepository;
    private final AccountRepository accountRepository;
    private final AiAnalysisClient aiClient;
    private final PlatformTransactionManager transactionManager;

    public AiAnalysisResponse analyze(Long projectId, Long dataId, AuthUser user, AiAnalysisRequest request) {
        String requestedAt = Times.nowString();

        // ① 사전 검증 (읽기 전용 트랜잭션)
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        AiRequest aiRequest = readOnly.execute(status -> prepare(projectId, dataId, user, request));

        // ② AI 서버 호출 (트랜잭션·잠금 밖)
        AiResult result = null;
        AiClientException failure = null;
        try {
            result = aiClient.analyze(aiRequest);
        } catch (AiClientException e) {
            failure = e;
        }

        // ③ 결과 저장 (data 행 잠금 후 version 부여). 실패한 호출도 FAILED 이력으로 저장한다.
        AiAnalysis entry = buildEntry(aiRequest, user, requestedAt, result, failure);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> save(projectId, dataId, user, entry));

        if (failure != null) {
            ErrorCode code = failure.getFailure() == AiClientException.Failure.AI_SERVER_TIMEOUT
                    ? ErrorCode.AI_SERVER_TIMEOUT : ErrorCode.AI_SERVER_ERROR;
            throw new BusinessException(code, failure.getFailure().publicMessage()
                    + " (실패 이력이 version " + entry.getVersion() + "로 저장되었습니다.)");
        }
        return AiAnalysisResponse.of(entry, user.name());
    }

    private AiRequest prepare(Long projectId, Long dataId, AuthUser user, AiAnalysisRequest request) {
        ProjectContext ctx = projectAccess.read(projectId, user);
        ctx.requireWriter();
        RecordContext rc = recordAccess.read(ctx, dataId);
        recordAccess.requireNotInvalidationType(rc);
        if (statusResolver.resolve(dataId).invalidatedBy() != null) {
            throw new BusinessException(ErrorCode.RECORD_INVALIDATED);
        }
        RecordMetadata metadata = rc.metadata();
        if (metadata.getAiAnalyses().size() >= RecordMetadata.MAX_AI_ANALYSES) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_LIMIT_EXCEEDED);
        }

        List<FileEntry> targets = resolveTargets(metadata, request == null ? null : request.targetStorageIds());
        Map<Long, Storage> storages = storageRepository.findAllById(targets.stream().map(FileEntry::getStorageId).toList())
                .stream().collect(Collectors.toMap(Storage::getStorageId, Function.identity()));
        List<AiImage> images = new ArrayList<>();
        for (FileEntry file : targets) {
            Storage storage = storages.get(file.getStorageId());
            if (storage == null) {
                throw new BusinessException(ErrorCode.NO_ANALYZABLE_FILES);
            }
            images.add(new AiImage(file.getStorageId(), file.getOriginalName(), file.getContentType(), storage.getImage()));
        }
        return new AiRequest(dataId, rc.record().getType(), metadata.getProcess(), images);
    }

    private static List<FileEntry> resolveTargets(RecordMetadata metadata, List<Long> requested) {
        List<FileEntry> images = metadata.getActiveFiles().stream()
                .filter(f -> f.getContentType() != null && f.getContentType().startsWith("image/"))
                .toList();
        if (requested == null || requested.isEmpty()) {
            if (images.isEmpty()) {
                throw new BusinessException(ErrorCode.NO_ANALYZABLE_FILES);
            }
            return images.stream()
                    .sorted(Comparator.comparing((FileEntry f) -> f.getUploadedAt() == null ? "" : f.getUploadedAt())
                            .thenComparing(FileEntry::getStorageId).reversed())
                    .limit(MAX_TARGETS)
                    .toList();
        }
        Set<Long> unique = new HashSet<>(requested);
        if (unique.size() != requested.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "targetStorageIds에 중복된 값이 있습니다.");
        }
        List<FileEntry> result = new ArrayList<>();
        for (Long storageId : requested) {
            FileEntry file = images.stream().filter(f -> storageId.equals(f.getStorageId())).findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                            "storageId " + storageId + "는 이 기록의 이미지가 아닙니다."));
            result.add(file);
        }
        return result;
    }

    private static AiAnalysis buildEntry(AiRequest request, AuthUser user, String requestedAt, AiResult result,
                                         AiClientException failure) {
        AiAnalysis entry = new AiAnalysis();
        entry.setAnalysisId(UUID.randomUUID().toString());
        entry.setTargetStorageIds(request.images().stream().map(AiImage::storageId).toList());
        entry.setRequestedBy(user.uid().toString());
        entry.setRequestedAt(requestedAt);
        if (failure == null) {
            entry.setStatus(AiAnalysisStatus.SUCCESS);
            entry.setModel(result.model());
            entry.setSummary(result.summary());
            entry.setResult(result.result());
        } else {
            entry.setStatus(AiAnalysisStatus.FAILED);
            entry.setErrorCode(failure.getFailure().name());
            entry.setErrorMessage(failure.getFailure().publicMessage());
        }
        return entry;
    }

    private void save(Long projectId, Long dataId, AuthUser user, AiAnalysis entry) {
        ProjectContext ctx = projectAccess.share(projectId, user);
        RecordContext rc = recordAccess.lock(ctx, dataId);
        RecordMetadata metadata = rc.metadata();
        if (metadata.getAiAnalyses().size() >= RecordMetadata.MAX_AI_ANALYSES) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_LIMIT_EXCEEDED);
        }
        int version = metadata.getAiAnalyses().stream()
                .map(AiAnalysis::getVersion)
                .filter(v -> v != null)
                .max(Integer::compare)
                .orElse(0) + 1;
        entry.setVersion(version);
        entry.setCompletedAt(Times.nowString());
        metadata.getAiAnalyses().add(entry);
        recordAccess.saveMetadata(rc.record(), metadata);
    }

    @Transactional(readOnly = true)
    public List<AiAnalysisResponse> list(Long projectId, Long dataId, AuthUser user) {
        RecordContext rc = recordAccess.read(projectAccess.read(projectId, user), dataId);
        List<AiAnalysis> analyses = rc.metadata().getAiAnalyses().stream()
                .sorted(Comparator.comparing((AiAnalysis a) -> a.getVersion() == null ? 0 : a.getVersion()).reversed())
                .toList();
        Map<UUID, String> names = requesterNames(analyses);
        return analyses.stream().map(a -> AiAnalysisResponse.of(a, names.get(parse(a.getRequestedBy())))).toList();
    }

    @Transactional(readOnly = true)
    public AiAnalysisResponse latest(Long projectId, Long dataId, AuthUser user, boolean successOnly) {
        RecordContext rc = recordAccess.read(projectAccess.read(projectId, user), dataId);
        AiAnalysis latest = rc.metadata().getAiAnalyses().stream()
                .filter(a -> a.getVersion() != null)
                .filter(a -> !successOnly || a.getStatus() == AiAnalysisStatus.SUCCESS)
                .max(Comparator.comparing(AiAnalysis::getVersion))
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_ANALYSIS_NOT_FOUND));
        return AiAnalysisResponse.of(latest, requesterNames(List.of(latest)).get(parse(latest.getRequestedBy())));
    }

    private Map<UUID, String> requesterNames(List<AiAnalysis> analyses) {
        Set<UUID> uids = analyses.stream().map(a -> parse(a.getRequestedBy())).filter(u -> u != null)
                .collect(Collectors.toSet());
        return accountRepository.findAllById(uids).stream()
                .collect(Collectors.toMap(Account::getUid, Account::getName));
    }

    private static UUID parse(String uid) {
        try {
            return uid == null ? null : UUID.fromString(uid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
