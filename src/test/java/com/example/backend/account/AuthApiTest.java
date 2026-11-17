package com.example.backend.account;

import com.example.backend.support.IntegrationTest;
import com.example.backend.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends IntegrationTest {

    private Map<String, Object> signupBody(String loginId) {
        Map<String, Object> body = new HashMap<>();
        body.put("loginId", loginId);
        body.put("password", TestFixtures.PASSWORD);
        body.put("name", "김현장");
        body.put("roleLevel", 1);
        body.put("organization", "OO건설");
        body.put("phone", "010-1234-5678");
        return body;
    }

    @Test
    void signup_success_hashesPasswordAndHidesIt() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupBody("kim.site01"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/accounts/me"))
                .andExpect(jsonPath("$.uid").isNotEmpty())
                .andExpect(jsonPath("$.loginId").value("kim.site01"))
                .andExpect(jsonPath("$.roleLevel").value(1))
                .andExpect(jsonPath("$.roleName").value("FIELD_WORKER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM account WHERE login_id = 'kim.site01'", String.class);
        assertThat(hash).isNotEqualTo(TestFixtures.PASSWORD).startsWith("$2");
    }

    @Test
    void signup_duplicateLoginId_returns409() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(signupBody("dup.user")))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(signupBody("dup.user"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
    }

    @Test
    void signup_invalidFields_returns400WithFieldErrors() throws Exception {
        Map<String, Object> body = signupBody("ab");
        body.put("password", "weak");
        body.put("roleLevel", 4);

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'loginId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'roleLevel')]").exists());
    }

    @Test
    void signup_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void login_success_returnsTokenValidFor2Hours() throws Exception {
        signup("lee.contractor", 2, "이시공");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "lee.contractor", "password", TestFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.account.roleName").value("CONTRACTOR"));
    }

    @Test
    void login_wrongPasswordOrUnknownId_returnsSame401() throws Exception {
        signup("kim.site01", 1, "김현장");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "kim.site01", "password", "Wrong123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "nobody", "password", "Wrong123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void login_ignoresInvalidBearerHeader() throws Exception {
        signup("kim.site01", 1, "김현장");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer invalid.token.value")
                        .content(json(Map.of("loginId", "kim.site01", "password", TestFixtures.PASSWORD))))
                .andExpect(status().isOk());
    }
}
