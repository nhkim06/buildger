package com.example.backend.construction;

import com.example.backend.support.IntegrationTest;
import com.example.backend.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorrectionApiTest extends IntegrationTest {

    private String owner;
    private String member;
    private String otherMember;
    private String supervisor;
    private long projectId;
    private long original;

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("lee.contractor", 2);
        member = signupAndLogin("kim.site01", 1);
        otherMember = signupAndLogin("park.site02", 1);
        supervisor = signupAndLogin("kim.supervisor", 3);
        projectId = createProject(owner, "현장");
        addMember(owner, projectId, "kim.site01", "MEMBER");
        addMember(owner, projectId, "park.site02", "MEMBER");
        addMember(owner, projectId, "kim.supervisor", "VIEWER");
        original = createRecord(member, projectId, "3층 슬래브 철근 배근");
        uploadImages(member, projectId, original, TestImages.png(1));
    }

    private String recordUrl(long dataId) {
        return "/api/projects/" + projectId + "/records/" + dataId;
    }

    private Map<String, Object> correctionBody(String reason, String title) {
        Map<String, Object> body = recordBody(title);
        body.put("reason", reason);
        return body;
    }

    private long amend(String token, long target, String title) throws Exception {
        return body(mockMvc.perform(post(recordUrl(target) + "/corrections").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(correctionBody("오기재 정정", title))))
                .andExpect(status().isCreated()).andReturn()).get("dataId").asLong();
    }

    private String metadataText(long dataId) {
        return jdbcTemplate.queryForObject("SELECT metadata::text FROM data WHERE data_id = ?", String.class, dataId);
    }

    @Test
    void amend_unsignedRecord_returns409() throws Exception {
        mockMvc.perform(post(recordUrl(original) + "/corrections").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(correctionBody("정정", "x"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_LOCKED"));
    }

    @Test
    void amend_signedRecord_createsLinkedRecordWithoutChangingOriginal() throws Exception {
        confirm(supervisor, projectId, original);
        String before = metadataText(original);

        mockMvc.perform(post(recordUrl(original) + "/corrections").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(correctionBody("층 정보 오기재 (3F → 4F)", "4층 슬래브 철근 배근"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("4층 슬래브 철근 배근"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.files.length()").value(0))
                .andExpect(jsonPath("$.correction.kind").value("AMENDMENT"))
                .andExpect(jsonPath("$.correction.originalDataId").value(original))
                .andExpect(jsonPath("$.correction.rootDataId").value(original))
                .andExpect(jsonPath("$.correction.reason").value("층 정보 오기재 (3F → 4F)"))
                .andExpect(jsonPath("$.correction.correctedBy.name").value("kim.site01-name"));

        // 원본 행의 metadata는 한 글자도 바뀌지 않는다.
        assertThat(metadataText(original)).isEqualTo(before);
        mockMvc.perform(get(recordUrl(original)).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.supersededByDataId").value(original + 1))
                .andExpect(jsonPath("$.integrity.status").value("VERIFIED"));
        mockMvc.perform(get("/api/projects/" + projectId + "/records").param("status", "SUPERSEDED")
                        .header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dataId").value(original));
    }

    @Test
    void amend_rules() throws Exception {
        confirm(supervisor, projectId, original);

        mockMvc.perform(post(recordUrl(original) + "/corrections").header("Authorization", bearer(otherMember))
                        .contentType(MediaType.APPLICATION_JSON).content(json(correctionBody("정정", "x"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(recordUrl(original) + "/corrections").header("Authorization", bearer(supervisor))
                        .contentType(MediaType.APPLICATION_JSON).content(json(correctionBody("정정", "x"))))
                .andExpect(status().isForbidden());
        Map<String, Object> noReason = recordBody("x");
        mockMvc.perform(post(recordUrl(original) + "/corrections").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(noReason)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));

        long first = amend(member, original, "정정 1");
        mockMvc.perform(post(recordUrl(original) + "/corrections").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(correctionBody("정정", "x"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_ALREADY_CORRECTED"));
        // 정정된 이전 버전에는 서명할 수 없다.
        String inspector = signupAndLogin("han.inspector", 2);
        addMember(owner, projectId, "han.inspector", "VIEWER");
        mockMvc.perform(confirmRequest(inspector, projectId, original, "APPROVED", null, TestImages.png(77)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_SUPERSEDED"));
        assertThat(first).isGreaterThan(original);
    }

    @Test
    void correctionChain_andHistory() throws Exception {
        confirm(supervisor, projectId, original);
        long first = amend(member, original, "정정 1");
        uploadImages(member, projectId, first, TestImages.png(2));
        confirm(supervisor, projectId, first);
        long second = amend(owner, first, "정정 2");

        mockMvc.perform(get(recordUrl(second)).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.correction.originalDataId").value(first))
                .andExpect(jsonPath("$.correction.rootDataId").value(original));

        for (long id : new long[]{original, first, second}) {
            mockMvc.perform(get(recordUrl(id) + "/history").header("Authorization", bearer(supervisor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rootDataId").value(original))
                    .andExpect(jsonPath("$.currentDataId").value(second))
                    .andExpect(jsonPath("$.currentStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.items[0].kind").value("ORIGINAL"))
                    .andExpect(jsonPath("$.items[0].status").value("SUPERSEDED"))
                    .andExpect(jsonPath("$.items[1].kind").value("AMENDMENT"))
                    .andExpect(jsonPath("$.items[1].reason").value("오기재 정정"))
                    .andExpect(jsonPath("$.items[2].locked").value(false));
        }
    }

    @Test
    void deletingUnsignedCorrection_reactivatesOriginal() throws Exception {
        confirm(supervisor, projectId, original);
        long correction = amend(member, original, "정정");

        mockMvc.perform(delete(recordUrl(correction)).header("Authorization", bearer(member)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(recordUrl(original)).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        amend(member, original, "다시 정정");
    }

    @Test
    void invalidate_signedRecord() throws Exception {
        long unsigned = createRecord(member, projectId, "서명 전");
        mockMvc.perform(post(recordUrl(unsigned) + "/invalidation").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "오등록"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_LOCKED"));

        confirm(supervisor, projectId, original);
        String before = metadataText(original);
        mockMvc.perform(post(recordUrl(original) + "/invalidation").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "오등록"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(recordUrl(original) + "/invalidation").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        JsonNode invalidation = body(mockMvc.perform(post(recordUrl(original) + "/invalidation")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "다른 현장 사진이 잘못 등록됨"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INVALIDATION"))
                .andExpect(jsonPath("$.title").value("기록 #" + original + " 무효화"))
                .andExpect(jsonPath("$.description").value("다른 현장 사진이 잘못 등록됨"))
                .andExpect(jsonPath("$.location.floor").value("3F"))
                .andExpect(jsonPath("$.correction.kind").value("INVALIDATION"))
                .andReturn());
        long invalidationId = invalidation.get("dataId").asLong();

        assertThat(metadataText(original)).isEqualTo(before);
        mockMvc.perform(get(recordUrl(original)).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.status").value("INVALIDATED"))
                .andExpect(jsonPath("$.invalidatedByDataId").value(invalidationId));
        mockMvc.perform(get(recordUrl(original) + "/history").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.currentDataId").value(invalidationId))
                .andExpect(jsonPath("$.currentStatus").value("INVALIDATED"))
                .andExpect(jsonPath("$.items[1].kind").value("INVALIDATION"));

        // 이미 무효화된 기록: 재무효화·정정·서명 불가
        mockMvc.perform(post(recordUrl(original) + "/invalidation").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "x"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_INVALIDATED"));
        mockMvc.perform(post(recordUrl(original) + "/corrections").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(correctionBody("x", "x"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_INVALIDATED"));
        // 무효화 기록에는 이미지 업로드 불가
        mockMvc.perform(multipart(recordUrl(invalidationId) + "/files")
                        .file(new MockMultipartFile("files", "a.png", "image/png", TestImages.png(5)))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RECORD_TYPE"));
    }

    /** 같은 원본에 대한 정정·무효화를 동시에 요청해도 하나만 생성된다. (docs/api.md 4.3) */
    @Test
    void concurrentCorrections_onlyOneSucceeds() throws Exception {
        confirm(supervisor, projectId, original);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String title = "동시 정정 " + i;
            tasks.add(() -> mockMvc.perform(post(recordUrl(original) + "/corrections")
                            .header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(json(correctionBody("정정", title))))
                    .andReturn().getResponse().getStatus());
        }
        for (int i = 0; i < 2; i++) {
            tasks.add(() -> mockMvc.perform(post(recordUrl(original) + "/invalidation")
                            .header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "무효"))))
                    .andReturn().getResponse().getStatus());
        }
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : executor.invokeAll(tasks)) {
            statuses.add(f.get());
        }
        executor.shutdownNow();

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s != 201).allMatch(s -> s == 409);
        Integer links = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM data WHERE metadata->'correction'->>'originalDataId' = ?",
                Integer.class, String.valueOf(original));
        assertThat(links).isEqualTo(1);
    }
}
