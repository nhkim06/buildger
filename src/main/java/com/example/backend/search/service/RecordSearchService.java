package com.example.backend.search.service;

import com.example.backend.construction.dto.LocationResponse;
import com.example.backend.construction.dto.RecordResponse;
import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.construction.mapper.RecordMapper;
import com.example.backend.construction.repository.RecordQuery;
import com.example.backend.construction.repository.RecordQueryRepository;
import com.example.backend.global.common.PageResponse;
import com.example.backend.global.common.Times;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.service.ProjectAccessService;
import com.example.backend.search.dto.RecordSearchRequest;
import com.example.backend.search.dto.RecordSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 참여 중인 모든 프로젝트 대상 시공 이력 검색 (docs/api.md 14.1).
 * 접근 범위(참여 중·삭제되지 않은 프로젝트)는 조건과 무관하게 항상 적용한다.
 */
@Service
@RequiredArgsConstructor
public class RecordSearchService {

    static final int MAX_PERIOD_DAYS = 366;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private final ProjectAccessService projectAccess;
    private final RecordQueryRepository recordQueryRepository;
    private final RecordMapper recordMapper;

    @Transactional(readOnly = true)
    public PageResponse<RecordSearchResponse> search(AuthUser user, RecordSearchRequest request) {
        LocalDate from = request.from();
        LocalDate to = request.to();
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "from은 to보다 이후일 수 없습니다.");
            }
            if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "검색 기간은 최대 366일입니다.");
            }
        } else if (from != null) {
            to = from.plusDays(MAX_PERIOD_DAYS - 1);
        } else if (to != null) {
            from = to.minusDays(MAX_PERIOD_DAYS - 1);
        }

        boolean radius = request.latitude() != null || request.longitude() != null || request.radiusMeters() != null;
        if (radius && (request.latitude() == null || request.longitude() == null || request.radiusMeters() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "latitude, longitude, radiusMeters는 함께 입력해야 합니다.");
        }
        String[] sort = (request.sort() == null ? "capturedAt,desc" : request.sort()).split(",");
        if ("distance".equals(sort[0]) && !radius) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "distance 정렬은 반경 검색에서만 사용할 수 있습니다.");
        }
        if (request.projectId() != null) {
            projectAccess.read(request.projectId(), user);
        }

        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        RecordQuery query = RecordQuery.builder()
                .projectId(request.projectId())
                .accessibleUid(user.uid())
                .memberJson(projectAccess.memberContainmentJson(user.uid()))
                .fromUtc(from == null ? null : Times.startOfDay(from))
                .toUtc(to == null ? null : Times.endOfDay(to))
                .type(request.type())
                .status(request.status())
                .site(trimToNull(request.site()))
                .building(trimToNull(request.building()))
                .floor(trimToNull(request.floor()))
                .zone(trimToNull(request.zone()))
                .address(trimToNull(request.address()))
                .keyword(trimToNull(request.keyword()))
                .keywordIncludesProcess(true)
                .confirmationStatus(request.confirmationStatus())
                .hasAiAnalysis(request.hasAiAnalysis())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .radiusMeters(request.radiusMeters())
                .sortField(sort[0])
                .ascending("asc".equals(sort[1]))
                .page(page)
                .size(size)
                .build();
        RecordQueryRepository.Result result = recordQueryRepository.search(query);

        List<ConstructionRecord> records = result.content();
        List<RecordResponse> responses = recordMapper.toResponses(records);
        List<RecordSearchResponse> content = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            RecordResponse response = responses.get(i);
            content.add(RecordSearchResponse.of(response, records.get(i).getProject().getPname(),
                    radius ? distance(request.latitude(), request.longitude(), response.location()) : null));
        }
        return PageResponse.of(content, page, size, result.total());
    }

    private static Double distance(double lat, double lng, LocationResponse location) {
        if (location == null || location.latitude() == null || location.longitude() == null) {
            return null;
        }
        double dLat = Math.toRadians(location.latitude() - lat);
        double dLng = Math.toRadians(location.longitude() - lng);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat))
                * Math.cos(Math.toRadians(location.latitude())) * Math.pow(Math.sin(dLng / 2), 2);
        double meters = 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
        return Math.round(meters * 10) / 10.0;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
