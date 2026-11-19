package com.example.backend.construction.mapper;

import com.example.backend.account.dto.AccountSummary;
import com.example.backend.account.entity.Account;
import com.example.backend.account.repository.AccountRepository;
import com.example.backend.construction.dto.AiAnalysisSummary;
import com.example.backend.construction.dto.CorrectionResponse;
import com.example.backend.construction.dto.LocationResponse;
import com.example.backend.construction.dto.RecordResponse;
import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.construction.entity.Correction;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.construction.service.RecordAccessService;
import com.example.backend.construction.service.RecordIntegrityVerifier;
import com.example.backend.construction.service.RecordStatusResolver;
import com.example.backend.construction.service.RecordStatusResolver.StatusInfo;
import com.example.backend.file.dto.FileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ConstructionRecord(+metadata JSONB) → RecordResponse 변환.
 * 상태(정정·무효화)와 정정 작성자 이름은 목록 단위로 한 번에 조회한다. (N+1 방지)
 */
@Component
@RequiredArgsConstructor
public class RecordMapper {

    private final RecordAccessService recordAccess;
    private final RecordStatusResolver statusResolver;
    private final RecordIntegrityVerifier integrityVerifier;
    private final AccountRepository accountRepository;

    public RecordResponse toResponse(ConstructionRecord record, RecordMetadata metadata, boolean verifyFiles) {
        Map<Long, StatusInfo> statuses = statusResolver.resolve(List.of(record.getDataId()));
        return toResponse(record, metadata, statuses.get(record.getDataId()), accountsFor(List.of(metadata)),
                verifyFiles);
    }

    public List<RecordResponse> toResponses(List<ConstructionRecord> records) {
        List<RecordMetadata> metadataList = records.stream().map(recordAccess::readMetadata).toList();
        Map<Long, StatusInfo> statuses = statusResolver.resolve(
                records.stream().map(ConstructionRecord::getDataId).toList());
        Map<UUID, Account> accounts = accountsFor(metadataList);
        List<RecordResponse> result = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            ConstructionRecord record = records.get(i);
            result.add(toResponse(record, metadataList.get(i), statuses.get(record.getDataId()), accounts, false));
        }
        return result;
    }

    private RecordResponse toResponse(ConstructionRecord record, RecordMetadata metadata, StatusInfo status,
                                      Map<UUID, Account> accounts, boolean verifyFiles) {
        return new RecordResponse(
                record.getDataId(),
                record.getProjectId(),
                record.getType(),
                metadata.getTitle(),
                metadata.getDescription(),
                metadata.getProcess(),
                metadata.getCapturedAt(),
                LocationResponse.from(metadata.getLocation()),
                record.getUrl(),
                AccountSummary.from(record.getUploadedBy()),
                metadata.getActiveFiles().stream().map(FileResponse::from).toList(),
                status.status(),
                metadata.isLocked(),
                metadata.getConfirmationState(),
                metadata.getConfirmations().size(),
                correction(metadata.getCorrection(), accounts),
                status.supersededBy(),
                status.invalidatedBy(),
                metadata.getLatestAiAnalysis().map(AiAnalysisSummary::from).orElse(null),
                integrityVerifier.verify(record, metadata, verifyFiles),
                metadata.getCreatedAt(),
                metadata.getUpdatedAt());
    }

    public CorrectionResponse correction(Correction correction, Map<UUID, Account> accounts) {
        if (correction == null) {
            return null;
        }
        UUID uid = parse(correction.getCorrectedBy());
        Account account = uid == null ? null : accounts.get(uid);
        return new CorrectionResponse(correction.getKind(), correction.getOriginalDataId(), correction.getRootDataId(),
                correction.getReason(),
                new CorrectionResponse.Person(uid, account == null ? null : account.getName()),
                correction.getCorrectedAt());
    }

    /** 정정 작성자 이름 조회용 계정 맵 */
    public Map<UUID, Account> accountsFor(List<RecordMetadata> metadataList) {
        Set<UUID> uids = new HashSet<>();
        for (RecordMetadata metadata : metadataList) {
            if (metadata.getCorrection() != null) {
                UUID uid = parse(metadata.getCorrection().getCorrectedBy());
                if (uid != null) {
                    uids.add(uid);
                }
            }
        }
        if (uids.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findAllById(uids).stream()
                .collect(Collectors.toMap(Account::getUid, Function.identity()));
    }

    private static UUID parse(String uid) {
        try {
            return uid == null ? null : UUID.fromString(uid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
