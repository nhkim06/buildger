package com.example.backend.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 임베디드 PostgreSQL + 실제 스키마로 API를 호출하는 통합 테스트 기반 클래스.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", EmbeddedPostgres::jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE data, storage, project, account RESTART IDENTITY CASCADE");
    }

    protected String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 회원가입 후 로그인해 Access Token을 반환한다. */
    protected String signupAndLogin(String loginId, int roleLevel) throws Exception {
        signup(loginId, roleLevel, loginId + "-name");
        return login(loginId);
    }

    protected JsonNode signup(String loginId, int roleLevel, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", loginId,
                                "password", TestFixtures.PASSWORD,
                                "name", name,
                                "roleLevel", roleLevel,
                                "organization", "OO건설"))))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("signup failed: " + result.getResponse().getContentAsString());
        }
        return body(result);
    }

    protected String login(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", loginId, "password", TestFixtures.PASSWORD))))
                .andReturn();
        return body(result).get("accessToken").asString();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** 프로젝트를 생성하고 projectId를 반환한다. */
    protected long createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "startDate", "2026-03-01", "endDate", "2027-08-31",
                                "status", "IN_PROGRESS"))))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("create project failed: " + result.getResponse().getContentAsString());
        }
        return body(result).get("projectId").asLong();
    }

    /** 참여자를 추가한다. */
    protected void addMember(String managerToken, long projectId, String loginId, String projectRole) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", loginId, "projectRole", projectRole))))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("add member failed: " + result.getResponse().getContentAsString());
        }
    }

    /** 시공 기록을 생성하고 dataId를 반환한다. */
    protected long createRecord(String token, long projectId, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/records")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("create record failed: " + result.getResponse().getContentAsString());
        }
        return body(result).get("dataId").asLong();
    }

    protected long createRecord(String token, long projectId, String title) throws Exception {
        return createRecord(token, projectId, recordBody(title));
    }

    protected static Map<String, Object> recordBody(String title) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("type", "PHOTO");
        body.put("title", title);
        body.put("description", "D13 @200 배근 완료");
        body.put("process", "철근콘크리트공사");
        body.put("capturedAt", "2026-11-16T14:20:00+09:00");
        body.put("location", Map.of("site", "A동", "building", "본관", "floor", "3F", "zone", "X1-X3",
                "latitude", 37.501274, "longitude", 127.039585));
        return body;
    }

    /** 이미지를 업로드하고 storageId 목록을 반환한다. */
    protected List<Long> uploadImages(String token, long projectId, long dataId, byte[]... images) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/projects/" + projectId + "/records/" + dataId + "/files");
        for (int i = 0; i < images.length; i++) {
            boolean jpeg = (images[i][0] & 0xFF) == 0xFF;
            request.file(new org.springframework.mock.web.MockMultipartFile(
                    "files", "photo" + i + (jpeg ? ".jpg" : ".png"), jpeg ? "image/jpeg" : "image/png", images[i]));
        }
        MvcResult result = mockMvc.perform(request.header("Authorization", bearer(token))).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("upload failed: " + result.getResponse().getContentAsString());
        }
        List<Long> ids = new java.util.ArrayList<>();
        body(result).forEach(node -> ids.add(node.get("storageId").asLong()));
        return ids;
    }

    /** 확인·서명 요청 (multipart: request JSON 파트 + signature 이미지) */
    protected org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder confirmRequest(
            String token, long projectId, long dataId, String status, String comment, byte[] signature) {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("status", status);
        if (comment != null) {
            request.put("comment", comment);
        }
        var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/projects/" + projectId + "/records/" + dataId + "/confirmations");
        builder.file(new org.springframework.mock.web.MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                json(request).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (signature != null) {
            builder.file(new org.springframework.mock.web.MockMultipartFile("signature", "sign.png", "image/png", signature));
        }
        builder.header("Authorization", bearer(token));
        return builder;
    }

    /** 서명하고 응답을 반환한다. */
    protected JsonNode confirm(String token, long projectId, long dataId) throws Exception {
        MvcResult result = mockMvc.perform(confirmRequest(token, projectId, dataId, "APPROVED", "확인 완료",
                TestImages.png(9000 + (int) dataId))).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("confirm failed: " + result.getResponse().getContentAsString());
        }
        return body(result);
    }

    protected String uidOf(String loginId) {
        return jdbcTemplate.queryForObject("SELECT uid::text FROM account WHERE login_id = ?", String.class, loginId);
    }
}
