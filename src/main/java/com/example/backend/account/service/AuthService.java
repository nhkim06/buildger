package com.example.backend.account.service;

import com.example.backend.account.dto.AccountResponse;
import com.example.backend.account.dto.LoginRequest;
import com.example.backend.account.dto.LoginResponse;
import com.example.backend.account.dto.SignupRequest;
import com.example.backend.account.entity.Account;
import com.example.backend.account.entity.RoleLevel;
import com.example.backend.account.repository.AccountRepository;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /** 계정 존재 여부와 관계없이 비슷한 응답 시간을 갖도록 비교에 사용하는 더미 해시 */
    private String dummyHash;

    @Transactional
    public AccountResponse signup(SignupRequest request) {
        if (accountRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        Account account = new Account(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                RoleLevel.fromLevel(request.roleLevel()),
                request.name().strip(),
                blankToNull(request.organization()),
                blankToNull(request.phone()));
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // 동시 가입으로 인한 login_id UNIQUE 위반
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<Account> found = accountRepository.findByLoginId(request.loginId());
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        Account account = found.get();
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        String token = jwtTokenProvider.issue(account.getUid(), account.getRoleLevel());
        return new LoginResponse("Bearer", token, jwtTokenProvider.expiresInSeconds(), AccountResponse.from(account));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password-for-timing");
        }
        return dummyHash;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
