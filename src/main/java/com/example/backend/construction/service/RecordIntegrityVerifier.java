package com.example.backend.construction.service;

import com.example.backend.construction.dto.IntegrityResponse;
import com.example.backend.construction.dto.IntegrityResponse.FileIntegrityStatus;
import com.example.backend.construction.dto.IntegrityResponse.IntegrityStatus;
import com.example.backend.construction.entity.Confirmation;
import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.construction.entity.FileEntry;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.file.entity.Storage;
import com.example.backend.file.repository.StorageRepository;
import com.example.backend.global.common.Hashes;
import com.example.backend.global.common.Times;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 서명 해시와 이미지 바이너리 해시 검증 (docs/api.md 4.4).
 * 불일치는 API를 거치지 않은 변경을 의미하므로 dataId만 포함한 WARN 로그를 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordIntegrityVerifier {

    private final RecordSnapshotHasher hasher;
    private final StorageRepository storageRepository;

    public IntegrityResponse verify(ConstructionRecord record, RecordMetadata metadata, boolean verifyFiles) {
        String now = Times.nowString();
        List<Confirmation> confirmations = metadata.getConfirmations();
        FileIntegrityStatus fileStatus = verifyFiles
                ? (filesMatch(metadata) ? FileIntegrityStatus.VERIFIED : FileIntegrityStatus.MISMATCH)
                : FileIntegrityStatus.NOT_CHECKED;
        if (fileStatus == FileIntegrityStatus.MISMATCH) {
            log.warn("Image hash mismatch detected: dataId={}", record.getDataId());
        }
        if (confirmations.isEmpty()) {
            return new IntegrityResponse(IntegrityStatus.NOT_SIGNED, null, null, fileStatus, now);
        }
        String current = hasher.hash(record, metadata);
        boolean allMatch = snapshotMatches(confirmations, current);
        if (!allMatch) {
            log.warn("Record snapshot hash mismatch detected: dataId={}", record.getDataId());
        }
        return new IntegrityResponse(allMatch ? IntegrityStatus.VERIFIED : IntegrityStatus.MISMATCH,
                confirmations.get(0).getRecordSnapshotSha256(), current, fileStatus, now);
    }

    /** 저장된 모든 서명 해시가 현재 해시와 같은지 */
    public boolean snapshotMatches(List<Confirmation> confirmations, String currentHash) {
        return confirmations.stream()
                .allMatch(c -> Objects.equals(c.getRecordSnapshotSha256(), currentHash));
    }

    /** 삭제되지 않은 첨부 이미지와 서명 이미지의 바이너리 해시가 저장된 값과 같은지 */
    public boolean filesMatch(RecordMetadata metadata) {
        Map<Long, String> expected = new LinkedHashMap<>();
        for (FileEntry file : metadata.getActiveFiles()) {
            expected.put(file.getStorageId(), file.getSha256());
        }
        for (Confirmation confirmation : metadata.getConfirmations()) {
            if (confirmation.getSignature() != null) {
                expected.put(confirmation.getSignature().getStorageId(), confirmation.getSignature().getSha256());
            }
        }
        return attachmentsMatch(expected);
    }

    /** 첨부 이미지(삭제되지 않은 것)만 검증한다. 서명 등록 전 검사에 사용한다. */
    public boolean activeFilesMatch(RecordMetadata metadata) {
        Map<Long, String> expected = new LinkedHashMap<>();
        for (FileEntry file : metadata.getActiveFiles()) {
            expected.put(file.getStorageId(), file.getSha256());
        }
        return attachmentsMatch(expected);
    }

    private boolean attachmentsMatch(Map<Long, String> expected) {
        if (expected.isEmpty()) {
            return true;
        }
        Map<Long, Storage> storages = new LinkedHashMap<>();
        storageRepository.findAllById(expected.keySet()).forEach(s -> storages.put(s.getStorageId(), s));
        for (Map.Entry<Long, String> entry : expected.entrySet()) {
            Storage storage = storages.get(entry.getKey());
            if (storage == null || !Hashes.sha256Hex(storage.getImage()).equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
