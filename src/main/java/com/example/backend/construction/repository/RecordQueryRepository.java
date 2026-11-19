package com.example.backend.construction.repository;

import com.example.backend.construction.entity.ConfirmationState;
import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.global.common.SqlLike;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSONB 조건을 조합하는 시공 기록 목록·검색 Native Query (docs/api.md 9.2, 14.1).
 * 모든 값은 파라미터로 바인딩하며 SQL 문자열에 사용자 입력을 직접 넣지 않는다.
 */
@Repository
public class RecordQueryRepository {

    private static final String LAT = "(CASE WHEN jsonb_typeof(d.metadata->'location'->'latitude') = 'number' "
            + "THEN CAST(d.metadata->'location'->>'latitude' AS double precision) END)";
    private static final String LNG = "(CASE WHEN jsonb_typeof(d.metadata->'location'->'longitude') = 'number' "
            + "THEN CAST(d.metadata->'location'->>'longitude' AS double precision) END)";
    /** Haversine 거리(m) */
    private static final String DISTANCE = "(2 * 6371000 * asin(LEAST(1, sqrt("
            + "power(sin(radians(" + LAT + " - :lat) / 2), 2) + cos(radians(:lat)) * cos(radians(" + LAT + ")) "
            + "* power(sin(radians(" + LNG + " - :lng) / 2), 2)))))";
    private static final String CORRECTION_EXISTS = "EXISTS (SELECT 1 FROM data c "
            + "WHERE c.project_id = d.project_id AND c.metadata->'deletion' IS NULL "
            + "AND c.metadata->'correction'->>'originalDataId' = CAST(d.data_id AS text) "
            + "AND c.metadata->'correction'->>'kind' = '%s')";

    @PersistenceContext
    private EntityManager entityManager;

    public Result search(RecordQuery q) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = buildWhere(q, params);

        Query count = entityManager.createNativeQuery(
                "SELECT count(*) FROM data d JOIN project p ON p.project_id = d.project_id WHERE " + where);
        params.forEach(count::setParameter);
        long total = ((Number) count.getSingleResult()).longValue();

        Query select = entityManager.createNativeQuery(
                "SELECT d.* FROM data d JOIN project p ON p.project_id = d.project_id WHERE " + where
                        + " ORDER BY " + orderBy(q), ConstructionRecord.class);
        params.forEach(select::setParameter);
        select.setFirstResult(q.page() * q.size());
        select.setMaxResults(q.size());
        @SuppressWarnings("unchecked")
        List<ConstructionRecord> content = select.getResultList();
        return new Result(content, total);
    }

    private String buildWhere(RecordQuery q, Map<String, Object> params) {
        StringBuilder sql = new StringBuilder(
                "d.metadata->'deletion' IS NULL AND p.detail->'deletion' IS NULL");

        if (q.projectId() != null) {
            sql.append(" AND d.project_id = :projectId");
            params.put("projectId", q.projectId());
        }
        if (q.accessibleUid() != null) {
            sql.append(" AND (p.created_by = :uid OR p.detail->'members' @> CAST(:memberJson AS jsonb))");
            params.put("uid", q.accessibleUid());
            params.put("memberJson", q.memberJson());
        }
        if (q.fromUtc() != null) {
            sql.append(" AND d.metadata->>'capturedAt' >= :fromUtc");
            params.put("fromUtc", q.fromUtc());
        }
        if (q.toUtc() != null) {
            sql.append(" AND d.metadata->>'capturedAt' <= :toUtc");
            params.put("toUtc", q.toUtc());
        }
        if (q.type() != null) {
            sql.append(" AND d.type = :type");
            params.put("type", q.type().name());
        }
        if (q.locked() != null) {
            sql.append(q.locked()
                    ? " AND jsonb_array_length(COALESCE(d.metadata->'confirmations', CAST('[]' AS jsonb))) > 0"
                    : " AND jsonb_array_length(COALESCE(d.metadata->'confirmations', CAST('[]' AS jsonb))) = 0");
        }
        likeFilter(sql, params, "process", "d.metadata->>'process'", q.process());
        likeFilter(sql, params, "site", "d.metadata->'location'->>'site'", q.site());
        likeFilter(sql, params, "building", "d.metadata->'location'->>'building'", q.building());
        likeFilter(sql, params, "zone", "d.metadata->'location'->>'zone'", q.zone());
        likeFilter(sql, params, "address", "d.metadata->'location'->>'address'", q.address());
        if (q.floor() != null) {
            sql.append(" AND lower(d.metadata->'location'->>'floor') = lower(:floor)");
            params.put("floor", q.floor());
        }
        if (q.keyword() != null) {
            sql.append(" AND (d.metadata->>'title' ILIKE :keyword ESCAPE '\\'"
                    + " OR d.metadata->>'description' ILIKE :keyword ESCAPE '\\'");
            if (q.keywordIncludesProcess()) {
                sql.append(" OR d.metadata->>'process' ILIKE :keyword ESCAPE '\\'");
            }
            sql.append(")");
            params.put("keyword", SqlLike.contains(q.keyword()));
        }
        if (q.uploadedBy() != null) {
            sql.append(" AND d.uploaded_by = :uploadedBy");
            params.put("uploadedBy", q.uploadedBy());
        }
        if (q.confirmationStatus() != null) {
            if (q.confirmationStatus() == ConfirmationState.NONE) {
                sql.append(" AND jsonb_array_length(COALESCE(d.metadata->'confirmations', CAST('[]' AS jsonb))) = 0");
            } else {
                sql.append(" AND d.metadata->'confirmations'->-1->>'status' = :confirmationStatus");
                params.put("confirmationStatus", q.confirmationStatus().name());
            }
        }
        if (q.status() != null) {
            String superseded = CORRECTION_EXISTS.formatted("AMENDMENT");
            String invalidated = CORRECTION_EXISTS.formatted("INVALIDATION");
            switch (q.status()) {
                case ACTIVE -> sql.append(" AND NOT ").append(superseded).append(" AND NOT ").append(invalidated);
                case SUPERSEDED -> sql.append(" AND ").append(superseded);
                case INVALIDATED -> sql.append(" AND ").append(invalidated);
            }
        }
        if (q.hasAiAnalysis() != null) {
            sql.append(q.hasAiAnalysis() ? " AND " : " AND NOT ")
                    .append("EXISTS (SELECT 1 FROM jsonb_array_elements(COALESCE(d.metadata->'aiAnalyses', "
                            + "CAST('[]' AS jsonb))) a WHERE a->>'status' = 'SUCCESS')");
        }
        if (q.radiusMeters() != null) {
            double latDelta = q.radiusMeters() / 111_320d;
            double lngDelta = q.radiusMeters() / (111_320d * Math.max(0.01, Math.cos(Math.toRadians(q.latitude()))));
            sql.append(" AND ").append(LAT).append(" BETWEEN :minLat AND :maxLat")
                    .append(" AND ").append(LNG).append(" BETWEEN :minLng AND :maxLng")
                    .append(" AND ").append(DISTANCE).append(" <= :radius");
            params.put("minLat", q.latitude() - latDelta);
            params.put("maxLat", q.latitude() + latDelta);
            params.put("minLng", q.longitude() - lngDelta);
            params.put("maxLng", q.longitude() + lngDelta);
            params.put("lat", q.latitude());
            params.put("lng", q.longitude());
            params.put("radius", q.radiusMeters().doubleValue());
        }
        return sql.toString();
    }

    private static void likeFilter(StringBuilder sql, Map<String, Object> params, String name, String column,
                                   String value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" ILIKE :").append(name).append(" ESCAPE '\\'");
            params.put(name, SqlLike.contains(value));
        }
    }

    private static String orderBy(RecordQuery q) {
        String direction = q.ascending() ? "ASC" : "DESC";
        return switch (q.sortField()) {
            case "distance" -> DISTANCE + " ASC, d.data_id DESC";
            case "createdAt" -> "d.metadata->>'createdAt' " + direction + " NULLS LAST, d.data_id " + direction;
            default -> "d.metadata->>'capturedAt' " + direction + " NULLS LAST, d.data_id " + direction;
        };
    }

    public record Result(List<ConstructionRecord> content, long total) {
    }
}
