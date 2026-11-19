package com.example.backend.construction;

import com.example.backend.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecordApiTest extends IntegrationTest {

    private String owner;
    private String member;
    private String otherMember;
    private String viewer;
    private long projectId;

    @BeforeEach
    void setUpProject() throws Exception {
        owner = signupAndLogin("lee.contractor", 2);
        member = signupAndLogin("kim.site01", 1);
        otherMember = signupAndLogin("park.site02", 1);
        viewer = signupAndLogin("choi.viewer", 3);
        projectId = createProject(owner, "현장");
        addMember(owner, projectId, "kim.site01", "MEMBER");
        addMember(owner, projectId, "park.site02", "MEMBER");
        addMember(owner, projectId, "choi.viewer", "VIEWER");
    }

    private String recordsUrl() {
        return "/api/projects/" + projectId + "/records";
    }

    /** 3단계에서는 서명 API가 없으므로 확인 항목을 직접 넣어 서명된 기록을 만든다. */
    private void simulateSignature(long dataId) {
        jdbcTemplate.update("UPDATE data SET metadata = jsonb_set(metadata, '{confirmations}', "
                + "CAST('[{\"confirmationId\":\"test\",\"status\":\"APPROVED\",\"recordSnapshotSha256\":\"x\"}]' AS jsonb)) "
                + "WHERE data_id = ?", dataId);
    }

    @Test
    void create_success_storesMetadataStructure() throws Exception {
        mockMvc.perform(post(recordsUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(recordBody("3층 슬래브 철근 배근"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", recordsUrl() + "/1"))
                .andExpect(jsonPath("$.dataId").value(1))
                .andExpect(jsonPath("$.type").value("PHOTO"))
                .andExpect(jsonPath("$.capturedAt").value("2026-11-16T05:20:00Z"))
                .andExpect(jsonPath("$.location.floor").value("3F"))
                .andExpect(jsonPath("$.uploadedBy.name").value("kim.site01-name"))
                .andExpect(jsonPath("$.files.length()").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.confirmationStatus").value("NONE"))
                .andExpect(jsonPath("$.integrity.status").value("NOT_SIGNED"));

        JsonNode metadata = jsonMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT metadata::text FROM data WHERE data_id = 1", String.class));
        assertThat(metadata.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(metadata.get("files").isArray()).isTrue();
        assertThat(metadata.get("confirmations").isArray()).isTrue();
        assertThat(metadata.get("aiAnalyses").isArray()).isTrue();
        assertThat(metadata.has("deletion")).isFalse();
        assertThat(metadata.has("correction")).isFalse();
        assertThat(metadata.get("createdAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        String uploader = jdbcTemplate.queryForObject("SELECT uploaded_by::text FROM data", String.class);
        assertThat(uploader).isEqualTo(uidOf("kim.site01"));
    }

    @Test
    void create_permissionAndValidationErrors() throws Exception {
        String outsider = signupAndLogin("outsider", 2);
        mockMvc.perform(post(recordsUrl()).header("Authorization", bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(recordBody("t"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PROJECT_ROLE"));
        mockMvc.perform(post(recordsUrl()).header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content(json(recordBody("t"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));

        Map<String, Object> invalidation = recordBody("t");
        invalidation.put("type", "INVALIDATION");
        mockMvc.perform(post(recordsUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(invalidation)))
                .andExpect(status().isBadRequest());

        Map<String, Object> future = recordBody("t");
        future.put("capturedAt", "2099-01-01T00:00:00+09:00");
        mockMvc.perform(post(recordsUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(future)))
                .andExpect(status().isBadRequest());

        Map<String, Object> halfCoordinate = recordBody("t");
        halfCoordinate.put("location", Map.of("latitude", 37.5));
        mockMvc.perform(post(recordsUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(halfCoordinate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        Map<String, Object> noTitle = recordBody("t");
        noTitle.remove("title");
        mockMvc.perform(post(recordsUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(noTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void list_filtersByDateLocationKeywordAndSort() throws Exception {
        Map<String, Object> a = recordBody("3층 배근");
        a.put("capturedAt", "2026-11-01T00:30:00+09:00"); // UTC 10-31 15:30 → KST 11-01
        Map<String, Object> b = recordBody("4층 거푸집");
        b.put("capturedAt", "2026-11-10T10:00:00+09:00");
        b.put("process", "형틀공사");
        b.put("location", Map.of("site", "B동", "floor", "4f"));
        Map<String, Object> c = recordBody("5층 타설 100%");
        c.put("capturedAt", "2026-11-15T23:59:00+09:00");
        c.put("type", "INSPECTION");
        createRecord(member, projectId, a);
        createRecord(otherMember, projectId, b);
        createRecord(member, projectId, c);

        // KST 날짜 경계
        mockMvc.perform(get(recordsUrl()).param("from", "2026-11-01").param("to", "2026-11-01")
                        .header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("3층 배근"));
        mockMvc.perform(get(recordsUrl()).param("from", "2026-11-15").header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.totalElements").value(1));
        // 층(대소문자 무시)·동 부분 일치·공정
        mockMvc.perform(get(recordsUrl()).param("floor", "4F").header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(recordsUrl()).param("site", "B").param("process", "형틀")
                        .header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.content[0].title").value("4층 거푸집"));
        // 키워드(LIKE 특수문자 이스케이프)·유형·작성자
        mockMvc.perform(get(recordsUrl()).param("keyword", "100%").header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(recordsUrl()).param("type", "INSPECTION").header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(recordsUrl()).param("uploadedBy", uidOf("park.site02"))
                        .header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.totalElements").value(1));
        // 정렬·페이지
        mockMvc.perform(get(recordsUrl()).param("sort", "capturedAt,asc").param("size", "2")
                        .header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.content[0].title").value("3층 배근"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get(recordsUrl()).header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.content[0].title").value("5층 타설 100%"));
    }

    @Test
    void list_invalidParams_return400() throws Exception {
        mockMvc.perform(get(recordsUrl()).param("from", "2026-11-10").param("to", "2026-11-01")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(recordsUrl()).param("sort", "title,asc").header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(recordsUrl()).param("from", "2026/11/10").header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(recordsUrl()).param("size", "0").header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_recordOfAnotherProject_returns404() throws Exception {
        long dataId = createRecord(member, projectId, "기록");
        long otherProject = createProject(owner, "다른 현장");

        mockMvc.perform(get(recordsUrl() + "/" + dataId).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("기록"));
        mockMvc.perform(get("/api/projects/" + otherProject + "/records/" + dataId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
    }

    @Test
    void update_byAuthorOrManager_andPermissionRules() throws Exception {
        long dataId = createRecord(member, projectId, "기록");

        mockMvc.perform(patch(recordsUrl() + "/" + dataId).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("description", "피복두께 30mm 확인", "location", Map.of("floor", "4F")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("피복두께 30mm 확인"))
                .andExpect(jsonPath("$.location.floor").value("4F"))
                .andExpect(jsonPath("$.location.site").doesNotExist())
                .andExpect(jsonPath("$.title").value("기록"));

        mockMvc.perform(patch(recordsUrl() + "/" + dataId).header("Authorization", bearer(otherMember))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(recordsUrl() + "/" + dataId).header("Authorization", bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(recordsUrl() + "/" + dataId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "NOTE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("NOTE"));
        mockMvc.perform(patch(recordsUrl() + "/" + dataId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "INVALIDATION"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(recordsUrl() + "/" + dataId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        String updatedBy = jdbcTemplate.queryForObject(
                "SELECT metadata->>'updatedBy' FROM data WHERE data_id = ?", String.class, dataId);
        assertThat(updatedBy).isEqualTo(uidOf("lee.contractor"));
    }

    @Test
    void signedRecord_cannotBeUpdatedOrDeleted() throws Exception {
        long dataId = createRecord(member, projectId, "기록");
        simulateSignature(dataId);

        mockMvc.perform(patch(recordsUrl() + "/" + dataId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "수정"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_LOCKED"));
        mockMvc.perform(delete(recordsUrl() + "/" + dataId).header("Authorization", bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_LOCKED"));
        mockMvc.perform(get(recordsUrl() + "/" + dataId).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.title").value("기록"));
    }

    @Test
    void delete_softDeletesAndKeepsRow() throws Exception {
        long dataId = createRecord(member, projectId, "기록");

        mockMvc.perform(delete(recordsUrl() + "/9999").header("Authorization", bearer(member)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(recordsUrl() + "/" + dataId).header("Authorization", bearer(otherMember)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(recordsUrl() + "/" + dataId).param("reason", "중복 등록")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(recordsUrl() + "/" + dataId).header("Authorization", bearer(member)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(recordsUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(0));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT metadata->>'title' AS title, metadata->'deletion'->>'reason' AS reason, "
                        + "metadata->'deletion'->>'deletedBy' AS by FROM data WHERE data_id = ?", dataId);
        assertThat(row.get("title")).isEqualTo("기록");
        assertThat(row.get("reason")).isEqualTo("중복 등록");
        assertThat(row.get("by")).isEqualTo(uidOf("kim.site01"));
    }

    @Test
    void deletedProject_hidesRecords() throws Exception {
        long dataId = createRecord(member, projectId, "기록");
        mockMvc.perform(delete("/api/projects/" + projectId).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(recordsUrl() + "/" + dataId).header("Authorization", bearer(member)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        Integer rows = jdbcTemplate.queryForObject("SELECT count(*) FROM data", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    /** 서로 다른 필드를 동시에 수정해도 JSONB 갱신 손실이 없다. (docs/api.md 4.1, 4.5) */
    @Test
    void concurrentUpdates_doNotLoseFields() throws Exception {
        long dataId = createRecord(member, projectId, "기록");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String field = i % 2 == 0 ? "description" : "process";
            String value = field + "-" + i;
            tasks.add(() -> mockMvc.perform(patch(recordsUrl() + "/" + dataId)
                            .header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(field, value))))
                    .andReturn().getResponse().getStatus());
        }
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : executor.invokeAll(tasks)) {
            statuses.add(f.get());
        }
        executor.shutdownNow();

        assertThat(statuses).containsOnly(200);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT metadata->>'description' AS d, metadata->>'process' AS p FROM data WHERE data_id = ?", dataId);
        // 마지막 요청 값이 무엇이든 두 필드 모두 이번 요청들 중 하나의 값이어야 한다. (덮어쓰기로 원래 값이 남지 않음)
        assertThat((String) row.get("d")).startsWith("description-");
        assertThat((String) row.get("p")).startsWith("process-");
    }
}
