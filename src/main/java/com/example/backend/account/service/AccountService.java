package com.example.backend.account.service;

import com.example.backend.account.dto.AccountResponse;
import com.example.backend.account.dto.AccountUpdateRequest;
import com.example.backend.account.dto.PasswordChangeRequest;
import com.example.backend.account.entity.Account;
import com.example.backend.account.repository.AccountRepository;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AccountResponse getMe(UUID uid) {
        return AccountResponse.from(findAccount(uid));
    }

    @Transactional
    public AccountResponse updateMe(UUID uid, AccountUpdateRequest request) {
        if (request.hasNoChanges()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경할 필드가 없습니다.");
        }
        Account account = findAccount(uid);
        if (request.name() != null) {
            account.changeName(request.name().strip());
        }
        if (request.organization() != null) {
            account.changeOrganization(blankToNull(request.organization()));
        }
        if (request.phone() != null) {
            account.changePhone(blankToNull(request.phone()));
        }
        return AccountResponse.from(account);
    }

    @Transactional
    public void changePassword(UUID uid, PasswordChangeRequest request) {
        Account account = findAccount(uid);
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }
        account.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private Account findAccount(UUID uid) {
        return accountRepository.findById(uid)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value.strip();
    }
}
