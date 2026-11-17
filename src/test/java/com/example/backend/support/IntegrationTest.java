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
}
