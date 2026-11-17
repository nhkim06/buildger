package com.example.backend.account;

import com.example.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountApiTest extends IntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void getMe_success() throws Exception {
        String token = signupAndLogin("kim.site01", 1);

        mockMvc.perform(get("/api/accounts/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("kim.site01"))
                .andExpect(jsonPath("$.roleName").value("FIELD_WORKER"));
    }

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/accounts/me"));
    }

    @Test
    void getMe_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/accounts/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getMe_withExpiredToken_returnsTokenExpired() throws Exception {
        String uid = signup("kim.site01", 1, "김현장").get("uid").asString();
        Instant past = Instant.now().minusSeconds(3 * 3600);
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(uid).claim("role", 1)
                        .issuedAt(past).expiresAt(past.plusSeconds(7200)).build())).getTokenValue();

        mockMvc.perform(get("/api/accounts/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void getMe_tokenOfDeletedAccount_returns401() throws Exception {
        Instant now = Instant.now();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(UUID.randomUUID().toString()).claim("role", 1)
                        .issuedAt(now).expiresAt(now.plusSeconds(600)).build())).getTokenValue();

        mockMvc.perform(get("/api/accounts/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void updateMe_changesOnlyGivenFields() throws Exception {
        String token = signupAndLogin("kim.site01", 1);

        mockMvc.perform(patch("/api/accounts/me").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("organization", "OO건설 품질팀", "phone", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("kim.site01-name"))
                .andExpect(jsonPath("$.organization").value("OO건설 품질팀"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void updateMe_noFields_returns400() throws Exception {
        String token = signupAndLogin("kim.site01", 1);

        mockMvc.perform(patch("/api/accounts/me").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void updateMe_blankName_returns400() throws Exception {
        String token = signupAndLogin("kim.site01", 1);

        mockMvc.perform(patch("/api/accounts/me").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void changePassword_success_thenLoginWithNewPassword() throws Exception {
        String token = signupAndLogin("kim.site01", 1);

        mockMvc.perform(patch("/api/accounts/me/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Passw0rd!", "newPassword", "N3wPassw0rd!"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "kim.site01", "password", "N3wPassw0rd!"))))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_wrongCurrent_returns401() throws Exception {
        String token = signupAndLogin("kim.site01", 1);

        mockMvc.perform(patch("/api/accounts/me/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Wrong123!", "newPassword", "N3wPassw0rd!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void changePassword_sameAsCurrent_returns400() throws Exception {
        String token = signupAndLogin("kim.site01", 1);

        mockMvc.perform(patch("/api/accounts/me/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Passw0rd!", "newPassword", "Passw0rd!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_AS_CURRENT_PASSWORD"));
    }
}
