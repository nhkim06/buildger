package com.example.backend.construction.service;

import com.example.backend.construction.entity.CorrectionKind;
import com.example.backend.construction.entity.RecordStatus;
import com.example.backend.construction.repository.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 기록 상태 계산 (docs/api.md 3.5). 원본 행을 변경하지 않기 위해, 이 기록을 참조하는
 * 다른 기록의 correction을 한 번에 조회해 계산한다.
 */
@Component
@RequiredArgsConstructor
public class RecordStatusResolver {

    private final RecordRepository recordRepository;

    public Map<Long, StatusInfo> resolve(Collection<Long> dataIds) {
        Map<Long, Long> supersededBy = new HashMap<>();
        Map<Long, Long> invalidatedBy = new HashMap<>();
        if (!dataIds.isEmpty()) {
            List<String> keys = dataIds.stream().map(String::valueOf).toList();
            for (Object[] row : recordRepository.findCorrectionLinks(keys)) {
                Long originalId = ((Number) row[0]).longValue();
                Long correctionId = ((Number) row[2]).longValue();
                if (CorrectionKind.INVALIDATION.name().equals(row[1])) {
                    invalidatedBy.putIfAbsent(originalId, correctionId);
                } else {
                    supersededBy.putIfAbsent(originalId, correctionId);
                }
            }
        }
        Map<Long, StatusInfo> result = new HashMap<>();
        for (Long id : dataIds) {
            result.put(id, StatusInfo.of(supersededBy.get(id), invalidatedBy.get(id)));
        }
        return result;
    }

    public StatusInfo resolve(Long dataId) {
        return resolve(List.of(dataId)).get(dataId);
    }

    public record StatusInfo(RecordStatus status, Long supersededBy, Long invalidatedBy) {

        static StatusInfo of(Long supersededBy, Long invalidatedBy) {
            RecordStatus status = invalidatedBy != null ? RecordStatus.INVALIDATED
                    : supersededBy != null ? RecordStatus.SUPERSEDED : RecordStatus.ACTIVE;
            return new StatusInfo(status, supersededBy, invalidatedBy);
        }
    }
}
