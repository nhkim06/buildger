package com.example.backend.global.security;

import com.example.backend.account.entity.Account;
import com.example.backend.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 토큰의 sub(uid)로 계정을 조회해 {@link AuthUser}를 만든다. 계정이 없으면 401로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AccountRepository accountRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID uid;
        try {
            uid = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        Account account = accountRepository.findById(uid)
                .orElseThrow(() -> new InvalidBearerTokenException("Account not found"));
        AuthUser user = new AuthUser(account.getUid(), account.getRoleLevel(), account.getName());
        return new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_LEVEL_" + account.getRoleLevel())));
    }
}
