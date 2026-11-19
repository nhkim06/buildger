package com.example.backend.construction.service;

import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.construction.entity.Correction;
import com.example.backend.construction.entity.FileEntry;
import com.example.backend.construction.entity.Location;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.global.common.Hashes;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 서명 스냅샷 해시 v1 (docs/api.md 4.4). 정규화 규칙은 이 클래스에서만 구현하며
 * 서명과 검증에 같은 코드를 사용한다.
 * - 키 사전순 정렬, 공백 없음, 정의된 키는 항상 포함(값 없으면 null)
 * - 위·경도는 BigDecimal 표기 문자열, 파일은 삭제되지 않은 것만 storageId 오름차순
 */
@Component
public class RecordSnapshotHasher {

    public static final int SNAPSHOT_VERSION = 1;

    private final JsonMapper mapper = JsonMapper.builder().build();

    public String hash(ConstructionRecord record, RecordMetadata metadata) {
        return Hashes.sha256Hex(canonicalJson(record, metadata).getBytes(StandardCharsets.UTF_8));
    }

    String canonicalJson(ConstructionRecord record, RecordMetadata metadata) {
        Map<String, Object> snapshot = new TreeMap<>();
        snapshot.put("snapshotVersion", SNAPSHOT_VERSION);
        snapshot.put("dataId", record.getDataId());
        snapshot.put("projectId", record.getProjectId());
        snapshot.put("type", record.getType());
        snapshot.put("url", record.getUrl());
        snapshot.put("uploadedBy", record.getUploadedByUid() == null ? null : record.getUploadedByUid().toString());
        snapshot.put("createdAt", metadata.getCreatedAt());
        snapshot.put("title", metadata.getTitle());
        snapshot.put("description", metadata.getDescription());
        snapshot.put("process", metadata.getProcess());
        snapshot.put("capturedAt", metadata.getCapturedAt());
        snapshot.put("location", location(metadata.getLocation()));
        snapshot.put("files", files(metadata.getActiveFiles()));
        snapshot.put("correction", correction(metadata.getCorrection()));
        return mapper.writeValueAsString(snapshot);
    }

    private static Map<String, Object> location(Location location) {
        if (location == null) {
            return null;
        }
        Map<String, Object> map = new TreeMap<>();
        map.put("address", location.getAddress());
        map.put("building", location.getBuilding());
        map.put("floor", location.getFloor());
        map.put("latitude", decimal(location.getLatitude()));
        map.put("longitude", decimal(location.getLongitude()));
        map.put("site", location.getSite());
        map.put("zone", location.getZone());
        return map;
    }

    private static List<Map<String, Object>> files(List<FileEntry> files) {
        List<Map<String, Object>> list = new ArrayList<>();
        files.stream()
                .sorted(Comparator.comparing(FileEntry::getStorageId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(file -> {
                    Map<String, Object> map = new TreeMap<>();
                    map.put("sha256", file.getSha256());
                    map.put("storageId", file.getStorageId());
                    list.add(map);
                });
        return list;
    }

    private static Map<String, Object> correction(Correction correction) {
        if (correction == null) {
            return null;
        }
        Map<String, Object> map = new TreeMap<>();
        map.put("correctedAt", correction.getCorrectedAt());
        map.put("correctedBy", correction.getCorrectedBy());
        map.put("kind", correction.getKind() == null ? null : correction.getKind().name());
        map.put("originalDataId", correction.getOriginalDataId());
        map.put("reason", correction.getReason());
        map.put("rootDataId", correction.getRootDataId());
        return map;
    }

    private static String decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
