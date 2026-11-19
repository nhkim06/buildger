package com.example.backend.ai;

import com.example.backend.ai.client.AiAnalysisClient;
import com.example.backend.ai.client.AiAnalysisClient.AiRequest;
import com.example.backend.ai.client.AiAnalysisClient.AiResult;
import com.example.backend.ai.client.AiClientException;
import com.example.backend.support.IntegrationTest;
import com.example.backend.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiAnalysisApiTest extends IntegrationTest {

    @MockitoBean
    private AiAnalysisClient aiClient;

    private String owner;
    private String member;
    private String viewer;
    private long projectId;
    private long dataId;
    private List<Long> storageIds;

    @BeforeEach
    void setUp() throws Exception {
        owner = signupAndLogin("lee.contractor", 2);
        member = signupAndLogin("kim.site01", 1);
        viewer = signupAndLogin("kim.supervisor", 3);
        projectId = createProject(owner, "현장");
        addMember(owner, projectId, "kim.site01", "MEMBER");
        addMember(owner, projectId, "kim.supervisor", "VIEWER");
        dataId = createRecord(member, projectId, "3층 슬래브 철근 배근");
        storageIds = uploadImages(member, projectId, dataId, TestImages.png(1), TestImages.jpeg(2));
        when(aiClient.analyze(any())).thenReturn(new AiResult("rebar-detector-v2", "철근 간격 평균 198mm, 기준 충족",
                Map.of("rebarSpacingMm", 198, "defects", List.of())));
    }

    private String analysesUrl() {
        return "/api/projects/" + projectId + "/records/" + dataId + "/ai-analyses";
    }

    @Test
    void analyze_success_callsAiServerAndStoresResult() throws Exception {
        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", analysesUrl() + "/latest"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.model").value("rebar-detector-v2"))
                .andExpect(jsonPath("$.result.rebarSpacingMm").value(198))
                .andExpect(jsonPath("$.targetStorageIds.length()").value(2))
                .andExpect(jsonPath("$.requestedBy.name").value("kim.site01-name"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());

        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(aiClient).analyze(captor.capture());
        assertThat(captor.getValue().dataId()).isEqualTo(dataId);
        assertThat(captor.getValue().images()).hasSize(2);
        assertThat(captor.getValue().images().get(0).bytes()).isEqualTo(TestImages.jpeg(2)); // 최근 업로드 순

        JsonNode stored = jsonMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT metadata->'aiAnalyses'->0 FROM data WHERE data_id = ?", String.class, dataId));
        assertThat(stored.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(stored.get("result").get("rebarSpacingMm").asInt()).isEqualTo(198);
        mockMvc.perform(get("/api/projects/" + projectId + "/records/" + dataId).header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.latestAiAnalysis.version").value(1))
                .andExpect(jsonPath("$.latestAiAnalysis.summary").value("철근 간격 평균 198mm, 기준 충족"));
    }

    @Test
    void analyze_explicitTargets_andValidation() throws Exception {
        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetStorageIds", List.of(storageIds.get(0))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetStorageIds[0]").value(storageIds.get(0)));

        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("targetStorageIds", List.of(999)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetStorageIds", List.of(storageIds.get(0), storageIds.get(0))))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetStorageIds", List.of(1, 2, 3, 4, 5, 6)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyze_aiServerFailure_storesFailedHistoryAndReturns5xx() throws Exception {
        doThrow(new AiClientException(AiClientException.Failure.AI_SERVER_TIMEOUT, "timeout", null))
                .when(aiClient).analyze(any());
        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_SERVER_TIMEOUT"));

        doThrow(new AiClientException(AiClientException.Failure.AI_INVALID_RESPONSE, "invalid", null))
                .when(aiClient).analyze(any());
        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_SERVER_ERROR"));

        mockMvc.perform(get(analysesUrl()).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].errorCode").value("AI_INVALID_RESPONSE"))
                .andExpect(jsonPath("$[1].errorCode").value("AI_SERVER_TIMEOUT"));
        mockMvc.perform(get(analysesUrl() + "/latest").header("Authorization", bearer(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_ANALYSIS_NOT_FOUND"));
        mockMvc.perform(get(analysesUrl() + "/latest").param("successOnly", "false")
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void analyze_permissionAndStateRules() throws Exception {
        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(viewer)))
                .andExpect(status().isForbidden());

        long noImage = createRecord(member, projectId, "사진 없음");
        mockMvc.perform(post("/api/projects/" + projectId + "/records/" + noImage + "/ai-analyses")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ANALYZABLE_FILES"));
        verify(aiClient, never()).analyze(any());

        jdbcTemplate.update("UPDATE data SET metadata = jsonb_set(metadata, '{aiAnalyses}', "
                + "(SELECT jsonb_agg(jsonb_build_object('analysisId', g::text, 'version', g, 'status', 'SUCCESS')) "
                + "FROM generate_series(1, 30) g)) WHERE data_id = ?", dataId);
        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ANALYSIS_LIMIT_EXCEEDED"));
    }

    @Test
    void analyze_signedRecord_isAllowed_andKeepsIntegrity() throws Exception {
        confirm(viewer, projectId, dataId);

        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/projects/" + projectId + "/records/" + dataId).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.integrity.status").value("VERIFIED"))
                .andExpect(jsonPath("$.latestAiAnalysis.status").value("SUCCESS"));

        // 무효화된 기록에는 분석을 추가할 수 없다.
        mockMvc.perform(post("/api/projects/" + projectId + "/records/" + dataId + "/invalidation")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "오등록"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_INVALIDATED"));
    }

    /** 동시 분석 요청은 저장 단계에서 잠금으로 직렬화되어 version이 중복되지 않는다. */
    @Test
    void concurrentAnalyses_getUniqueVersions() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> mockMvc.perform(post(analysesUrl()).header("Authorization", bearer(member)))
                    .andReturn().getResponse().getStatus());
        }
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : executor.invokeAll(tasks)) {
            statuses.add(f.get());
        }
        executor.shutdownNow();

        assertThat(statuses).containsOnly(201);
        List<Integer> versions = jdbcTemplate.queryForList(
                "SELECT CAST(a->>'version' AS int) FROM data, jsonb_array_elements(metadata->'aiAnalyses') a "
                        + "WHERE data_id = ? ORDER BY 1", Integer.class, dataId);
        assertThat(versions).containsExactly(1, 2, 3, 4, 5);
    }
}
