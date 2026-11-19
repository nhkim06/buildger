package com.example.backend.project;

import com.example.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectMemberApiTest extends IntegrationTest {

    private String owner;
    private long projectId;

    private void setUpProject() throws Exception {
        owner = signupAndLogin("lee.contractor", 2);
        projectId = createProject(owner, "현장");
    }

    private String membersUrl() {
        return "/api/projects/" + projectId + "/members";
    }

    @Test
    void add_andList_ownerFirstWithoutPhone() throws Exception {
        setUpProject();
        signup("kim.site01", 1, "김현장");

        mockMvc.perform(post(membersUrl()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "kim.site01", "projectRole", "MEMBER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginId").value("kim.site01"))
                .andExpect(jsonPath("$.projectRole").value("MEMBER"))
                .andExpect(jsonPath("$.roleName").value("FIELD_WORKER"));

        mockMvc.perform(get(membersUrl()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].projectRole").value("OWNER"))
                .andExpect(jsonPath("$[1].loginId").value("kim.site01"))
                .andExpect(jsonPath("$[0].phone").doesNotExist());

        String storedUid = jdbcTemplate.queryForObject(
                "SELECT detail->'members'->0->>'uid' FROM project", String.class);
        assertThat(storedUid).isEqualTo(uidOf("kim.site01"));
    }

    @Test
    void add_duplicateOrOwner_returns409() throws Exception {
        setUpProject();
        String worker = signupAndLogin("kim.site01", 1);
        addMember(owner, projectId, "kim.site01", "MEMBER");

        mockMvc.perform(post(membersUrl()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "kim.site01", "projectRole", "VIEWER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PROJECT_MEMBER"));
        mockMvc.perform(post(membersUrl()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "lee.contractor", "projectRole", "VIEWER"))))
                .andExpect(status().isConflict());
        assertThat(worker).isNotBlank();
    }

    @Test
    void add_validationAndPermissionErrors() throws Exception {
        setUpProject();
        String manager = signupAndLogin("choi.manager", 2);
        String member = signupAndLogin("kim.site01", 1);
        signup("park.viewer", 3, "박감리");
        addMember(owner, projectId, "choi.manager", "MANAGER");
        addMember(owner, projectId, "kim.site01", "MEMBER");

        // 존재하지 않는 계정
        mockMvc.perform(post(membersUrl()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "nobody", "projectRole", "VIEWER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        // OWNER 역할 지정 불가
        mockMvc.perform(post(membersUrl()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "park.viewer", "projectRole", "OWNER"))))
                .andExpect(status().isBadRequest());
        // MANAGER는 MANAGER를 지정할 수 없음
        mockMvc.perform(post(membersUrl()).header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "park.viewer", "projectRole", "MANAGER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PROJECT_ROLE"));
        // MEMBER는 참여자를 추가할 수 없음
        mockMvc.perform(post(membersUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "park.viewer", "projectRole", "VIEWER"))))
                .andExpect(status().isForbidden());
        // MANAGER는 VIEWER 지정 가능
        mockMvc.perform(post(membersUrl()).header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "park.viewer", "projectRole", "VIEWER"))))
                .andExpect(status().isCreated());
    }

    @Test
    void changeRole_rules() throws Exception {
        setUpProject();
        String manager = signupAndLogin("choi.manager", 2);
        signup("han.manager", 2, "한매니저");
        signup("kim.site01", 1, "김현장");
        addMember(owner, projectId, "choi.manager", "MANAGER");
        addMember(owner, projectId, "han.manager", "MANAGER");
        addMember(owner, projectId, "kim.site01", "MEMBER");

        mockMvc.perform(patch(membersUrl() + "/" + uidOf("kim.site01")).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("projectRole", "VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectRole").value("VIEWER"));
        mockMvc.perform(patch(membersUrl() + "/" + uidOf("lee.contractor")).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("projectRole", "VIEWER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_OWNER"));
        mockMvc.perform(patch(membersUrl() + "/" + uidOf("han.manager")).header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("projectRole", "VIEWER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(membersUrl() + "/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("projectRole", "VIEWER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        mockMvc.perform(patch(membersUrl() + "/not-a-uuid").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("projectRole", "VIEWER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void remove_selfLeave_ownerCannotLeave_removedLosesAccess() throws Exception {
        setUpProject();
        String member = signupAndLogin("kim.site01", 1);
        String manager = signupAndLogin("choi.manager", 2);
        signup("han.manager", 2, "한매니저");
        addMember(owner, projectId, "kim.site01", "MEMBER");
        addMember(owner, projectId, "choi.manager", "MANAGER");
        addMember(owner, projectId, "han.manager", "MANAGER");

        mockMvc.perform(delete(membersUrl() + "/" + uidOf("han.manager")).header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(membersUrl() + "/" + uidOf("lee.contractor")).header("Authorization", bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_OWNER"));

        mockMvc.perform(delete(membersUrl() + "/" + uidOf("kim.site01")).header("Authorization", bearer(member)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/projects/" + projectId).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        mockMvc.perform(delete(membersUrl() + "/" + uidOf("kim.site01")).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    /** 동시에 서로 다른 참여자를 추가해도 갱신 손실 없이 모두 저장된다. (docs/api.md 4.3, 4.5) */
    @Test
    void concurrentAdds_doNotLoseUpdates() throws Exception {
        setUpProject();
        int count = 8;
        for (int i = 0; i < count; i++) {
            signup("worker" + i, 1, "작업자" + i);
        }
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String loginId = "worker" + i;
            tasks.add(() -> mockMvc.perform(post(membersUrl()).header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginId", loginId, "projectRole", "MEMBER"))))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses).containsOnly(201);
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(detail->'members') FROM project", Integer.class);
        assertThat(stored).isEqualTo(count);
    }

    /** 같은 사용자를 동시에 추가해도 한 번만 등록된다. */
    @Test
    void concurrentDuplicateAdds_registerOnlyOnce() throws Exception {
        setUpProject();
        signup("kim.site01", 1, "김현장");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> mockMvc.perform(post(membersUrl()).header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginId", "kim.site01", "projectRole", "MEMBER"))))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s != 201).allMatch(s -> s == 409);
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(detail->'members') FROM project", Integer.class);
        assertThat(stored).isEqualTo(1);
    }

    private List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : executor.invokeAll(tasks)) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
