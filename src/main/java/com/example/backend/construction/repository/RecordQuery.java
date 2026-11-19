package com.example.backend.construction.repository;

import com.example.backend.construction.entity.ConfirmationState;
import com.example.backend.construction.entity.RecordStatus;
import com.example.backend.construction.entity.RecordType;
import lombok.Builder;

import java.util.UUID;

/**
 * 시공 기록 목록·검색 조건 (docs/api.md 9.2, 14.1).
 * projectId가 있으면 해당 프로젝트로, 없으면 accessibleUid가 참여 중인 모든 프로젝트로 범위를 제한한다.
 */
@Builder
public record RecordQuery(
        Long projectId,
        UUID accessibleUid,
        String memberJson,
        String fromUtc,
        String toUtc,
        RecordType type,
        RecordStatus status,
        Boolean locked,
        String process,
        String site,
        String building,
        String floor,
        String zone,
        String address,
        String keyword,
        boolean keywordIncludesProcess,
        UUID uploadedBy,
        ConfirmationState confirmationStatus,
        Boolean hasAiAnalysis,
        Double latitude,
        Double longitude,
        Integer radiusMeters,
        String sortField,
        boolean ascending,
        int page,
        int size
) {
}
