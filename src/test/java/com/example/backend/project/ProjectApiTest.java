package com.example.backend.project;

import com.example.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectApiTest extends IntegrationTest {

    @Test
    void create_byContractor_becomesOwnerAndStoresDetailJson() throws Exception {
        String token = signupAndLogin("lee.contractor", 2);

        mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "OO빌딩 신축공사", "address", "서울특별시 강남구",
                                "startDate", "2026-03-01", "endDate", "2027-08-31"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/projects/1"))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.name").value("OO빌딩 신축공사"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.owner.name").value("lee.contractor-name"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT pname, created_by::text AS owner, detail->>'schemaVersion' AS v, "
                        + "jsonb_typeof(detail->'members') AS members, detail ? 'deletion' AS deleted FROM project");
        assertThat(row.get("pname")).isEqualTo("OO빌딩 신축공사");
        assertThat(row.get("owner")).isEqualTo(uidOf("lee.contractor"));
        assertThat(row.get("v")).isEqualTo("1");
        assertThat(row.get("members")).isEqualTo("array");
        assertThat(row.get("deleted")).isEqualTo(false);
    }

    @Test
    void create_byFieldWorker_returns403() throws Exception {
        String token = signupAndLogin("kim.site01", 1);

        mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "현장"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
    }

    @Test
    void create_endBeforeStart_returns400() throws Exception {
        String token = signupAndLogin("lee.contractor", 2);

        mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "현장", "startDate", "2027-01-01", "endDate", "2026-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void create_withoutName_returns400() throws Exception {
        String token = signupAndLogin("lee.contractor", 2);

        mockMvc.perform(post("/api/projects").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("description", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void list_onlyAccessibleProjects_withFilters() throws Exception {
        String owner = signupAndLogin("lee.contractor", 2);
        String other = signupAndLogin("park.developer", 3);
        String worker = signupAndLogin("kim.site01", 1);
        long p1 = createProject(owner, "A동 신축_100%");
        createProject(owner, "B동 리모델링");
        createProject(other, "다른 회사 현장");
        addMember(owner, p1, "kim.site01", "MEMBER");

        mockMvc.perform(get("/api/projects").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("B동 리모델링"));

        mockMvc.perform(get("/api/projects").header("Authorization", bearer(worker)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].projectId").value(p1))
                .andExpect(jsonPath("$.content[0].myRole").value("MEMBER"))
                .andExpect(jsonPath("$.content[0].memberCount").value(2));

        // LIKE 특수문자(%, _)는 문자 그대로 검색된다.
        mockMvc.perform(get("/api/projects").param("keyword", "_100%").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/projects").param("keyword", "%").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/projects").param("status", "COMPLETED").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_invalidPaging_returns400() throws Exception {
        String owner = signupAndLogin("lee.contractor", 2);

        mockMvc.perform(get("/api/projects").param("size", "101").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/projects").param("status", "UNKNOWN").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_outsiderGets403_unknownGets404() throws Exception {
        String owner = signupAndLogin("lee.contractor", 2);
        String outsider = signupAndLogin("park.developer", 3);
        long projectId = createProject(owner, "현장");

        mockMvc.perform(get("/api/projects/" + projectId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-03-01"));
        mockMvc.perform(get("/api/projects/" + projectId).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        mockMvc.perform(get("/api/projects/999").header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void update_byManager_succeeds_byMember_forbidden() throws Exception {
        String owner = signupAndLogin("lee.contractor", 2);
        String manager = signupAndLogin("choi.manager", 2);
        String member = signupAndLogin("kim.site01", 1);
        long projectId = createProject(owner, "현장");
        addMember(owner, projectId, "choi.manager", "MANAGER");
        addMember(owner, projectId, "kim.site01", "MEMBER");

        mockMvc.perform(patch("/api/projects/" + projectId).header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "COMPLETED", "endDate", "2027-09-15"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endDate").value("2027-09-15"))
                .andExpect(jsonPath("$.myRole").value("MANAGER"))
                .andExpect(jsonPath("$.memberCount").value(3));

        mockMvc.perform(patch("/api/projects/" + projectId).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "PLANNED"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PROJECT_ROLE"));

        // 수정 후에도 참여자 목록(detail.members)은 보존된다.
        Integer members = jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(detail->'members') FROM project", Integer.class);
        assertThat(members).isEqualTo(2);
    }

    @Test
    void update_noFieldsOrInvalidMergedPeriod_returns400() throws Exception {
        String owner = signupAndLogin("lee.contractor", 2);
        long projectId = createProject(owner, "현장");

        mockMvc.perform(patch("/api/projects/" + projectId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        // 기존 startDate(2026-03-01)보다 앞선 endDate
        mockMvc.perform(patch("/api/projects/" + projectId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("endDate", "2026-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void delete_softDeletesAndHidesProject() throws Exception {
        String owner = signupAndLogin("lee.contractor", 2);
        long projectId = createProject(owner, "현장");

        mockMvc.perform(delete("/api/projects/" + projectId).param("reason", "프로젝트 종료")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(delete("/api/projects/" + projectId).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());

        JsonNode deletion = jsonMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT detail->'deletion' FROM project WHERE project_id = ?", String.class, projectId));
        assertThat(deletion.get("deletedBy").asString()).isEqualTo(uidOf("lee.contractor"));
        assertThat(deletion.get("reason").asString()).isEqualTo("프로젝트 종료");
        assertThat(deletion.get("deletedAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    @Test
    void delete_byManager_returns403() throws Exception {
        String owner = signupAndLogin("lee.contractor", 2);
        String manager = signupAndLogin("choi.manager", 2);
        long projectId = createProject(owner, "현장");
        addMember(owner, projectId, "choi.manager", "MANAGER");

        mockMvc.perform(delete("/api/projects/" + projectId).header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PROJECT_ROLE"));
    }
}
