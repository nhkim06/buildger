package com.example.backend.construction.service;

import com.example.backend.construction.dto.CorrectionRequest;
import com.example.backend.construction.dto.HistoryResponse;
import com.example.backend.construction.dto.InvalidationRequest;
import com.example.backend.construction.dto.RecordResponse;
import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.construction.entity.Correction;
import com.example.backend.construction.entity.CorrectionKind;
import com.example.backend.construction.entity.Location;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.construction.entity.RecordStatus;
import com.example.backend.construction.entity.RecordType;
import com.example.backend.construction.mapper.RecordMapper;
import com.example.backend.construction.repository.RecordRepository;
import com.example.backend.construction.service.RecordStatusResolver.StatusInfo;
import com.example.backend.global.common.Times;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.entity.ProjectRole;
import com.example.backend.project.service.ProjectAccessService;
import com.example.backend.project.service.ProjectContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 정정·무효화 (docs/api.md 10장).
 * 서명된 원본 행은 잠금만 하고 변경하지 않으며, 연결 관계는 새 기록의 metadata.correction에만 저장한다.
 * 원본 data 행 FOR UPDATE 잠금 상태에서 기존 정정·무효화 존재 여부를 확인하므로
 * 동일 원본에 대한 정정·무효화는 하나만 생성된다. (docs/api.md 4.3)
 */
@Service
@RequiredArgsConstructor
public class CorrectionService {

    private final ProjectAccessService projectAccess;
    private final RecordAccessService recordAccess;
    private final RecordService recordService;
    private final RecordStatusResolver statusResolver;
    private final RecordRepository recordRepository;
    private final RecordMapper recordMapper;

    @Transactional
    public RecordResponse amend(Long projectId, Long dataId, AuthUser user, CorrectionRequest request) {
        ProjectContext ctx = projectAccess.share(projectId, user);
        ctx.requireWriter();
        RecordContext original = recordAccess.lock(ctx, dataId);
        if (!ctx.role().canManage() && !(ctx.role() == ProjectRole.MEMBER && original.isAuthor())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PROJECT_ROLE, "원본 작성자 또는 OWNER·MANAGER만 정정할 수 있습니다.");
        }
        recordAccess.requireNotInvalidationType(original);
        requireCorrectable(original);

        Correction correction = newCorrection(CorrectionKind.AMENDMENT, original, request.reason(), user);
        ConstructionRecord created = recordService.newRecord(ctx, user, request.type(), request.title(),
                request.description(), request.process(), request.capturedAt(), request.location(),
                request.referenceUrl(), correction);
        return recordMapper.toResponse(created, recordAccess.readMetadata(created), false);
    }

    @Transactional
    public RecordResponse invalidate(Long projectId, Long dataId, AuthUser user, InvalidationRequest request) {
        ProjectContext ctx = projectAccess.share(projectId, user);
        ctx.requireManager();
        RecordContext original = recordAccess.lock(ctx, dataId);
        recordAccess.requireNotInvalidationType(original);
        requireCorrectable(original);

        String reason = request.reason().strip();
        RecordMetadata metadata = new RecordMetadata();
        metadata.setTitle("기록 #" + dataId + " 무효화");
        metadata.setDescription(reason);
        metadata.setCapturedAt(Times.nowString());
        metadata.setLocation(copy(original.metadata().getLocation()));
        metadata.setCorrection(newCorrection(CorrectionKind.INVALIDATION, original, reason, user));
        ConstructionRecord created = recordService.persist(ctx, user, RecordType.INVALIDATION, metadata, null);
        return recordMapper.toResponse(created, metadata, false);
    }

    @Transactional(readOnly = true)
    public HistoryResponse history(Long projectId, Long dataId, AuthUser user) {
        ProjectContext ctx = projectAccess.read(projectId, user);
        RecordContext rc = recordAccess.read(ctx, dataId);
        Correction correction = rc.metadata().getCorrection();
        Long rootId = correction != null && correction.getRootDataId() != null ? correction.getRootDataId() : dataId;

        List<ConstructionRecord> chain = recordRepository.findChain(rootId).stream()
                .filter(r -> r.getProjectId().equals(ctx.projectId()))
                .toList();
        Map<Long, StatusInfo> statuses = statusResolver.resolve(chain.stream().map(ConstructionRecord::getDataId).toList());

        List<HistoryResponse.Item> items = new ArrayList<>();
        for (ConstructionRecord record : chain) {
            RecordMetadata metadata = recordAccess.readMetadata(record);
            Correction c = metadata.getCorrection();
            items.add(new HistoryResponse.Item(
                    record.getDataId(),
                    c == null ? "ORIGINAL" : c.getKind().name(),
                    metadata.getTitle(),
                    statuses.get(record.getDataId()).status(),
                    metadata.isLocked(),
                    c == null ? null : c.getReason(),
                    record.getUploadedBy() == null ? null
                            : new HistoryResponse.Person(record.getUploadedBy().getUid(), record.getUploadedBy().getName()),
                    metadata.getCreatedAt()));
        }

        // 원본에서 정정 링크를 따라가 체인의 현재 기록을 찾는다.
        Long current = rootId;
        RecordStatus currentStatus = RecordStatus.ACTIVE;
        for (int guard = 0; guard <= chain.size(); guard++) {
            StatusInfo info = statuses.getOrDefault(current, StatusInfo.of(null, null));
            if (info.invalidatedBy() != null) {
                current = info.invalidatedBy();
                currentStatus = RecordStatus.INVALIDATED;
                break;
            }
            if (info.supersededBy() == null) {
                break;
            }
            current = info.supersededBy();
        }
        return new HistoryResponse(rootId, current, currentStatus, items);
    }

    /** 원본 행 잠금 후: 서명됨, 정정·무효화되지 않음 (docs/api.md 10장 공통 규칙) */
    private void requireCorrectable(RecordContext original) {
        if (!original.metadata().isLocked()) {
            throw new BusinessException(ErrorCode.RECORD_NOT_LOCKED);
        }
        StatusInfo status = statusResolver.resolve(original.dataId());
        if (status.invalidatedBy() != null) {
            throw new BusinessException(ErrorCode.RECORD_INVALIDATED);
        }
        if (status.supersededBy() != null) {
            throw new BusinessException(ErrorCode.RECORD_ALREADY_CORRECTED,
                    "이미 정정된 기록입니다. 최신 기록(#" + status.supersededBy() + ")을 대상으로 요청하세요.");
        }
    }

    private static Correction newCorrection(CorrectionKind kind, RecordContext original, String reason, AuthUser user) {
        Correction originalCorrection = original.metadata().getCorrection();
        Correction correction = new Correction();
        correction.setKind(kind);
        correction.setOriginalDataId(original.dataId());
        correction.setRootDataId(originalCorrection != null && originalCorrection.getRootDataId() != null
                ? originalCorrection.getRootDataId() : original.dataId());
        correction.setReason(reason.strip());
        correction.setCorrectedBy(user.uid().toString());
        correction.setCorrectedAt(Times.nowString());
        return correction;
    }

    private static Location copy(Location source) {
        if (source == null) {
            return null;
        }
        Location location = new Location();
        location.setSite(source.getSite());
        location.setBuilding(source.getBuilding());
        location.setFloor(source.getFloor());
        location.setZone(source.getZone());
        location.setAddress(source.getAddress());
        location.setLatitude(source.getLatitude());
        location.setLongitude(source.getLongitude());
        return location;
    }
}
