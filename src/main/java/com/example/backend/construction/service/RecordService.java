package com.example.backend.construction.service;

import com.example.backend.account.repository.AccountRepository;
import com.example.backend.construction.dto.LocationRequest;
import com.example.backend.construction.dto.RecordFilter;
import com.example.backend.construction.dto.RecordRequest;
import com.example.backend.construction.dto.RecordResponse;
import com.example.backend.construction.dto.RecordUpdateRequest;
import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.construction.entity.Correction;
import com.example.backend.construction.entity.Location;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.construction.entity.RecordType;
import com.example.backend.construction.mapper.RecordMapper;
import com.example.backend.construction.repository.RecordQuery;
import com.example.backend.construction.repository.RecordQueryRepository;
import com.example.backend.construction.repository.RecordRepository;
import com.example.backend.global.common.PageResponse;
import com.example.backend.global.common.Times;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.jsonb.Deletion;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.service.ProjectAccessService;
import com.example.backend.project.service.ProjectContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 시공 기록 CRUD (docs/api.md 9장)
 */
@Service
@RequiredArgsConstructor
public class RecordService {

    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final ProjectAccessService projectAccess;
    private final RecordAccessService recordAccess;
    private final RecordRepository recordRepository;
    private final RecordQueryRepository recordQueryRepository;
    private final AccountRepository accountRepository;
    private final RecordMapper recordMapper;

    @Transactional
    public RecordResponse create(Long projectId, AuthUser user, RecordRequest request) {
        ProjectContext ctx = projectAccess.share(projectId, user);
        ctx.requireWriter();
        ConstructionRecord record = newRecord(ctx, user, request.type(), request.title(), request.description(),
                request.process(), request.capturedAt(), request.location(), request.referenceUrl(), null);
        return recordMapper.toResponse(record, recordAccess.readMetadata(record), false);
    }

    /**
     * 새 기록 행을 만든다. 정정 기록 생성(10.1)에서도 사용한다.
     */
    public ConstructionRecord newRecord(ProjectContext ctx, AuthUser user, RecordType type, String title,
                                        String description, String process, OffsetDateTime capturedAt,
                                        LocationRequest location, String referenceUrl, Correction correction) {
        if (type == RecordType.INVALIDATION) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "INVALIDATION 유형은 무효화 API로만 생성할 수 있습니다.");
        }
        validateCapturedAt(capturedAt);

        RecordMetadata metadata = new RecordMetadata();
        metadata.setTitle(title.strip());
        metadata.setDescription(description);
        metadata.setProcess(process);
        metadata.setCapturedAt(Times.format(capturedAt));
        metadata.setLocation(toLocation(location));
        metadata.setCorrection(correction);
        return persist(ctx, user, type, metadata, blankToNull(referenceUrl));
    }

    /**
     * 새 data 행을 저장한다. createdAt·updatedAt은 서버 시각으로 설정한다.
     */
    public ConstructionRecord persist(ProjectContext ctx, AuthUser user, RecordType type, RecordMetadata metadata,
                                      String referenceUrl) {
        String now = Times.nowString();
        metadata.setCreatedAt(now);
        metadata.setUpdatedAt(now);
        ConstructionRecord record = new ConstructionRecord(ctx.project(), accountRepository.getReferenceById(user.uid()),
                type, null, referenceUrl);
        recordAccess.saveMetadata(record, metadata);
        return recordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public PageResponse<RecordResponse> list(Long projectId, AuthUser user, RecordFilter filter) {
        ProjectContext ctx = projectAccess.read(projectId, user);
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from은 to보다 이후일 수 없습니다.");
        }
        String[] sort = (filter.sort() == null ? "capturedAt,desc" : filter.sort()).split(",");
        int page = filter.page() == null ? 0 : filter.page();
        int size = filter.size() == null ? 20 : filter.size();
        RecordQuery query = RecordQuery.builder()
                .projectId(ctx.projectId())
                .fromUtc(filter.from() == null ? null : Times.startOfDay(filter.from()))
                .toUtc(filter.to() == null ? null : Times.endOfDay(filter.to()))
                .type(filter.type())
                .status(filter.status())
                .locked(filter.locked())
                .process(trimToNull(filter.process()))
                .site(trimToNull(filter.site()))
                .building(trimToNull(filter.building()))
                .floor(trimToNull(filter.floor()))
                .zone(trimToNull(filter.zone()))
                .keyword(trimToNull(filter.keyword()))
                .uploadedBy(filter.uploadedBy())
                .confirmationStatus(filter.confirmationStatus())
                .sortField(sort[0])
                .ascending("asc".equals(sort[1]))
                .page(page)
                .size(size)
                .build();
        RecordQueryRepository.Result result = recordQueryRepository.search(query);
        return PageResponse.of(recordMapper.toResponses(result.content()), page, size, result.total());
    }

    @Transactional(readOnly = true)
    public RecordResponse get(Long projectId, Long dataId, AuthUser user, boolean verifyFiles) {
        ProjectContext ctx = projectAccess.read(projectId, user);
        RecordContext rc = recordAccess.read(ctx, dataId);
        return recordMapper.toResponse(rc.record(), rc.metadata(), verifyFiles);
    }

    /** 서명 전 기록만 수정한다. data 행 잠금 후 서명 여부를 다시 확인한다. (docs/api.md 9.4) */
    @Transactional
    public RecordResponse update(Long projectId, Long dataId, AuthUser user, RecordUpdateRequest request) {
        if (request.hasNoChanges()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경할 필드가 없습니다.");
        }
        ProjectContext ctx = projectAccess.share(projectId, user);
        RecordContext rc = recordAccess.lock(ctx, dataId);
        recordAccess.requireModifiable(rc);
        recordAccess.requireNotLocked(rc);

        ConstructionRecord record = rc.record();
        RecordMetadata metadata = rc.metadata();
        RecordType currentType = record.getRecordType();
        if (currentType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 유형의 기록은 수정할 수 없습니다.");
        }
        if (request.type() != null) {
            if (request.type() == RecordType.INVALIDATION || currentType == RecordType.INVALIDATION) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "INVALIDATION 유형은 변경할 수 없습니다.");
            }
            record.changeType(request.type());
        }
        if (request.title() != null) {
            metadata.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            metadata.setDescription(request.description());
        }
        if (request.process() != null) {
            metadata.setProcess(request.process());
        }
        if (request.capturedAt() != null) {
            validateCapturedAt(request.capturedAt());
            metadata.setCapturedAt(Times.format(request.capturedAt()));
        }
        if (request.location() != null) {
            metadata.setLocation(toLocation(request.location()));
        }
        if (request.referenceUrl() != null) {
            record.changeUrl(blankToNull(request.referenceUrl()));
        }
        metadata.setUpdatedAt(Times.nowString());
        metadata.setUpdatedBy(user.uid().toString());
        recordAccess.saveMetadata(record, metadata);
        return recordMapper.toResponse(record, metadata, false);
    }

    /**
     * Soft Delete (docs/api.md 9.5). 서명된 기록은 삭제할 수 없고 무효화 기록(10.2)으로 처리한다.
     */
    @Transactional
    public void delete(Long projectId, Long dataId, AuthUser user, String reason) {
        ProjectContext ctx = projectAccess.share(projectId, user);
        RecordContext rc = recordAccess.lock(ctx, dataId);
        recordAccess.requireModifiable(rc);
        recordAccess.requireNotLocked(rc);
        if (!recordRepository.findCorrectionLinks(List.of(String.valueOf(dataId))).isEmpty()) {
            throw new BusinessException(ErrorCode.RECORD_HAS_CORRECTIONS);
        }
        RecordMetadata metadata = rc.metadata();
        metadata.setDeletion(new Deletion(Times.nowString(), user.uid().toString(), blankToNull(reason)));
        recordAccess.saveMetadata(rc.record(), metadata);
    }

    private static void validateCapturedAt(OffsetDateTime capturedAt) {
        if (capturedAt.toInstant().isAfter(Instant.now().plus(CLOCK_SKEW))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "capturedAt은 미래 시각일 수 없습니다.");
        }
    }

    static Location toLocation(LocationRequest request) {
        if (request == null) {
            return null;
        }
        Location location = new Location();
        location.setSite(request.site());
        location.setBuilding(request.building());
        location.setFloor(request.floor());
        location.setZone(request.zone());
        location.setAddress(request.address());
        location.setLatitude(request.latitude());
        location.setLongitude(request.longitude());
        return location;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String trimToNull(String value) {
        return blankToNull(value);
    }
}
