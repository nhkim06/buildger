package com.example.backend.confirmation;

import com.example.backend.support.IntegrationTest;
import com.example.backend.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfirmationApiTest extends IntegrationTest {

    private String owner;       // 시공사, OWNER
    private String member;      // 현장직, 기록 작성자
    private String supervisor;  // 시행사, VIEWER (확인·서명 담당)
    private String inspector;   // 시공사, VIEWER (확인·서명 담당)
    private long projectId;
    private long dataId;

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("lee.contractor", 2);
        member = signupAndLogin("kim.site01", 1);
        supervisor = signupAndLogin("kim.supervisor", 3);
        inspector = signupAndLogin("han.inspector", 2);
        projectId = createProject(owner, "현장");
        addMember(owner, projectId, "kim.site01", "MEMBER");
        addMember(owner, projectId, "kim.supervisor", "VIEWER");
        addMember(owner, projectId, "han.inspector", "VIEWER");
        dataId = createRecord(member, projectId, "3층 슬래브 철근 배근");
        uploadImages(member, projectId, dataId, TestImages.png(1), TestImages.jpeg(2));
    }

    private String recordUrl() {
        return "/api/projects/" + projectId + "/records/" + dataId;
    }

    @Test
    void confirm_success_storesSnapshotHashAndLocksRecord() throws Exception {
        JsonNode response = body(mockMvc.perform(
                        confirmRequest(supervisor, projectId, dataId, "APPROVED", "배근 간격 확인 완료", TestImages.png(50)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmer.name").value("kim.supervisor-name"))
                .andExpect(jsonPath("$.confirmer.roleName").value("DEVELOPER"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.comment").value("배근 간격 확인 완료"))
                .andExpect(jsonPath("$.signature.storageId").value(3))
                .andExpect(jsonPath("$.signature.downloadUrl").value("/api/files/3"))
                .andReturn());
        String hash = response.get("recordSnapshotSha256").asString();
        assertThat(hash).matches("[0-9a-f]{64}");

        mockMvc.perform(get(recordUrl()).param("verifyFiles", "true").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.confirmationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.confirmationCount").value(1))
                .andExpect(jsonPath("$.integrity.status").value("VERIFIED"))
                .andExpect(jsonPath("$.integrity.signedSnapshotSha256").value(hash))
                .andExpect(jsonPath("$.integrity.currentSnapshotSha256").value(hash))
                .andExpect(jsonPath("$.integrity.fileStatus").value("VERIFIED"));

        // 서명 이미지도 참여자가 조회할 수 있다.
        mockMvc.perform(get("/api/files/3").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(TestImages.png(50)));
        JsonNode stored = jsonMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT metadata->'confirmations'->0 FROM data WHERE data_id = ?", String.class, dataId));
        assertThat(stored.get("snapshotVersion").asInt()).isEqualTo(1);
        assertThat(stored.get("confirmerUid").asString()).isEqualTo(uidOf("kim.supervisor"));
    }

    @Test
    void signedRecord_cannotBeModifiedThroughAnyApi() throws Exception {
        confirm(supervisor, projectId, dataId);

        mockMvc.perform(patch(recordUrl()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "변경"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_LOCKED"));
        mockMvc.perform(delete(recordUrl()).header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
        mockMvc.perform(multipart(recordUrl() + "/files")
                        .file(new MockMultipartFile("files", "c.png", "image/png", TestImages.png(3)))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(recordUrl() + "/files/1").header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void confirm_permissionAndStateRules() throws Exception {
        String outsider = signupAndLogin("outsider", 3);
        // 현장직은 서명 불가
        mockMvc.perform(confirmRequest(member, projectId, dataId, "APPROVED", null, TestImages.png(50)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
        // 비참여자
        mockMvc.perform(confirmRequest(outsider, projectId, dataId, "APPROVED", null, TestImages.png(50)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        // 본인 작성 기록
        long ownRecord = createRecord(owner, projectId, "OWNER 작성");
        uploadImages(owner, projectId, ownRecord, TestImages.png(60));
        mockMvc.perform(confirmRequest(owner, projectId, ownRecord, "APPROVED", null, TestImages.png(50)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_CONFIRMATION_NOT_ALLOWED"));
        // 사진 없는 PHOTO 기록
        long noPhoto = createRecord(member, projectId, "사진 없음");
        mockMvc.perform(confirmRequest(supervisor, projectId, noPhoto, "APPROVED", null, TestImages.png(50)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_HAS_NO_FILES"));
        // NOTE 기록은 사진 없이도 서명 가능
        Map<String, Object> note = recordBody("메모");
        note.put("type", "NOTE");
        long noteId = createRecord(member, projectId, note);
        mockMvc.perform(confirmRequest(supervisor, projectId, noteId, "APPROVED", null, TestImages.png(50)))
                .andExpect(status().isCreated());
    }

    @Test
    void confirm_validationErrors() throws Exception {
        mockMvc.perform(confirmRequest(supervisor, projectId, dataId, "REJECTED", null, TestImages.png(50)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(confirmRequest(supervisor, projectId, dataId, "NONE", null, TestImages.png(50)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(confirmRequest(supervisor, projectId, dataId, "APPROVED", null, null))
                .andExpect(status().isBadRequest());
        mockMvc.perform(confirmRequest(supervisor, projectId, dataId, "APPROVED", null,
                        "not image".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isUnsupportedMediaType());
        byte[] tooLarge = Arrays.copyOf(TestImages.png(51), 1024 * 1024 + 1);
        mockMvc.perform(confirmRequest(supervisor, projectId, dataId, "APPROVED", null, tooLarge))
                .andExpect(status().isContentTooLarge());
        // 실패한 요청은 서명 이미지를 저장하지 않는다.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM storage", Integer.class)).isEqualTo(2);
    }

    @Test
    void confirm_duplicateStatusBySameConfirmer_returns409_butStatusChangeAllowed() throws Exception {
        confirm(supervisor, projectId, dataId);

        mockMvc.perform(confirmRequest(supervisor, projectId, dataId, "APPROVED", null, TestImages.png(70)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CONFIRMATION"));
        mockMvc.perform(confirmRequest(supervisor, projectId, dataId, "REJECTED", "피복 부족", TestImages.png(71)))
                .andExpect(status().isCreated());
        mockMvc.perform(confirmRequest(inspector, projectId, dataId, "APPROVED", null, TestImages.png(72)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(recordUrl() + "/confirmations").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].status").value("REJECTED"))
                .andExpect(jsonPath("$[2].confirmer.roleName").value("CONTRACTOR"));
        mockMvc.perform(get(recordUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.confirmationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.integrity.status").value("VERIFIED"));
    }

    /** API를 거치지 않은 DB 직접 변경은 조회 시 MISMATCH로 탐지되고 추가 서명이 거부된다. */
    @Test
    void directDbTampering_isDetected() throws Exception {
        confirm(supervisor, projectId, dataId);
        jdbcTemplate.update("UPDATE data SET metadata = jsonb_set(metadata, '{title}', CAST('\"조작된 제목\"' AS jsonb)) "
                + "WHERE data_id = ?", dataId);

        mockMvc.perform(get(recordUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.integrity.status").value("MISMATCH"));
        mockMvc.perform(confirmRequest(inspector, projectId, dataId, "APPROVED", null, TestImages.png(80)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTEGRITY_MISMATCH"));
    }

    @Test
    void tamperedImage_blocksSigning() throws Exception {
        jdbcTemplate.update("UPDATE storage SET image = ? WHERE storage_id = 1", TestImages.png(999));

        mockMvc.perform(confirmRequest(supervisor, projectId, dataId, "APPROVED", null, TestImages.png(50)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTEGRITY_MISMATCH"));
    }

    /** 서명과 수정이 동시에 들어와도, 서명 해시는 항상 최종 저장된 데이터와 일치한다. */
    @Test
    void concurrentSignAndUpdate_keepsHashConsistent() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        tasks.add(() -> mockMvc.perform(confirmRequest(supervisor, projectId, dataId, "APPROVED", null,
                TestImages.png(90))).andReturn().getResponse().getStatus());
        tasks.add(() -> mockMvc.perform(confirmRequest(inspector, projectId, dataId, "APPROVED", null,
                TestImages.png(91))).andReturn().getResponse().getStatus());
        for (int i = 0; i < 4; i++) {
            String title = "수정-" + i;
            tasks.add(() -> mockMvc.perform(patch(recordUrl()).header("Authorization", bearer(member))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", title))))
                    .andReturn().getResponse().getStatus());
        }
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : executor.invokeAll(tasks)) {
            statuses.add(f.get());
        }
        executor.shutdownNow();

        assertThat(statuses.subList(0, 2)).containsOnly(201);
        assertThat(statuses.subList(2, 6)).allMatch(s -> s == 200 || s == 409);
        mockMvc.perform(get(recordUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.confirmationCount").value(2))
                .andExpect(jsonPath("$.integrity.status").value("VERIFIED"));
    }
}
