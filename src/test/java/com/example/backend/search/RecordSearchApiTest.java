package com.example.backend.search;

import com.example.backend.support.IntegrationTest;
import com.example.backend.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecordSearchApiTest extends IntegrationTest {

    private static final String SEARCH = "/api/records/search";

    private String owner;
    private String member;
    private String supervisor;
    private long projectA;
    private long projectB;
    private long near;
    private long far;

    private Map<String, Object> body(String title, String capturedAt, Double lat, Double lng, String address) {
        Map<String, Object> body = recordBody(title);
        body.put("capturedAt", capturedAt);
        Map<String, Object> location = new java.util.HashMap<>();
        location.put("site", "A동");
        location.put("floor", "3F");
        if (lat != null) {
            location.put("latitude", lat);
            location.put("longitude", lng);
        }
        if (address != null) {
            location.put("address", address);
        }
        body.put("location", location);
        return body;
    }

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("lee.contractor", 2);
        member = signupAndLogin("kim.site01", 1);
        supervisor = signupAndLogin("kim.supervisor", 3);
        String other = signupAndLogin("other.company", 2);
        projectA = createProject(owner, "A 현장");
        projectB = createProject(owner, "B 현장");
        long projectC = createProject(other, "다른 회사 현장");
        addMember(owner, projectA, "kim.site01", "MEMBER");
        addMember(owner, projectB, "kim.site01", "MEMBER");
        addMember(owner, projectA, "kim.supervisor", "VIEWER");

        // 중심 (37.5, 127.0) 기준 약 111m, 약 1.1km
        near = createRecord(member, projectA, body("근거리 배근", "2026-11-10T10:00:00+09:00", 37.501, 127.0,
                "서울특별시 강남구 테헤란로 123"));
        far = createRecord(member, projectB, body("원거리 타설", "2026-11-12T10:00:00+09:00", 37.51, 127.0, null));
        createRecord(member, projectB, body("위치 없음 점검", "2025-01-05T10:00:00+09:00", null, null, null));
        createRecord(other, projectC, body("타사 기록", "2026-11-10T10:00:00+09:00", 37.501, 127.0, null));
        uploadImages(member, projectA, near, TestImages.png(1), TestImages.png(2));
    }

    @Test
    void search_onlyAccessibleProjects_withProjectNameAndThumbnail() throws Exception {
        mockMvc.perform(get(SEARCH).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].title").value("원거리 타설"))
                .andExpect(jsonPath("$.content[0].projectName").value("B 현장"))
                .andExpect(jsonPath("$.content[1].thumbnail.storageId").value(1))
                .andExpect(jsonPath("$.content[1].thumbnail.downloadUrl").value("/api/files/1"))
                .andExpect(jsonPath("$.content[1].fileCount").value(2))
                .andExpect(jsonPath("$.content[1].files").doesNotExist())
                .andExpect(jsonPath("$.content[1].distanceMeters").doesNotExist());

        mockMvc.perform(get(SEARCH).header("Authorization", bearer(supervisor)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(SEARCH).param("projectId", String.valueOf(projectB)).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(SEARCH).param("projectId", String.valueOf(projectB))
                        .header("Authorization", bearer(supervisor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(SEARCH).param("projectId", "999").header("Authorization", bearer(member)))
                .andExpect(status().isNotFound());
    }

    @Test
    void search_byDateRange() throws Exception {
        mockMvc.perform(get(SEARCH).param("from", "2026-11-10").param("to", "2026-11-11")
                        .header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dataId").value(near));
        // from만 지정하면 from부터 366일
        mockMvc.perform(get(SEARCH).param("from", "2025-01-01").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(SEARCH).param("from", "2025-01-01").param("to", "2026-01-02")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(SEARCH).param("from", "2026-11-12").param("to", "2026-11-10")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_byRadius_withDistance() throws Exception {
        mockMvc.perform(get(SEARCH).param("latitude", "37.5").param("longitude", "127.0").param("radiusMeters", "500")
                        .header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dataId").value(near))
                .andExpect(jsonPath("$.content[0].distanceMeters").value(closeTo(111.2, 0.5)));

        mockMvc.perform(get(SEARCH).param("latitude", "37.5").param("longitude", "127.0").param("radiusMeters", "2000")
                        .param("sort", "distance,asc").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].dataId").value(near))
                .andExpect(jsonPath("$.content[1].dataId").value(far))
                .andExpect(jsonPath("$.content[1].distanceMeters").value(closeTo(1112.0, 2.0)));
    }

    @Test
    void search_invalidRadiusParams_return400() throws Exception {
        mockMvc.perform(get(SEARCH).param("latitude", "37.5").param("radiusMeters", "500")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(SEARCH).param("latitude", "37.5").param("longitude", "127.0").param("radiusMeters", "5001")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(SEARCH).param("latitude", "91").param("longitude", "127.0").param("radiusMeters", "10")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(SEARCH).param("sort", "distance,asc").header("Authorization", bearer(member)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_byAddressKeywordAiAndConfirmation() throws Exception {
        mockMvc.perform(get(SEARCH).param("address", "테헤란로").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(1));
        // keyword는 process도 검색한다.
        mockMvc.perform(get(SEARCH).param("keyword", "철근콘크리트").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(3));

        jdbcTemplate.update("UPDATE data SET metadata = jsonb_set(metadata, '{aiAnalyses}', "
                + "CAST('[{\"analysisId\":\"a\",\"version\":1,\"status\":\"SUCCESS\"}]' AS jsonb)) WHERE data_id = ?", far);
        mockMvc.perform(get(SEARCH).param("hasAiAnalysis", "true").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].latestAiAnalysis.status").value("SUCCESS"));
        mockMvc.perform(get(SEARCH).param("hasAiAnalysis", "false").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(2));

        confirm(supervisor, projectA, near);
        mockMvc.perform(get(SEARCH).param("confirmationStatus", "APPROVED").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].locked").value(true))
                .andExpect(jsonPath("$.content[0].integrity.status").value("VERIFIED"));
        mockMvc.perform(get(SEARCH).param("confirmationStatus", "NONE").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(SEARCH).param("floor", "3f").param("site", "A").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void search_excludesDeletedRecordsAndProjects() throws Exception {
        mockMvc.perform(delete("/api/projects/" + projectB + "/records/" + far).header("Authorization", bearer(member)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(SEARCH).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(delete("/api/projects/" + projectB).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(SEARCH).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dataId").value(near));
    }
}
