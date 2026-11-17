package com.example.backend.account.dto;

/**
 * 계정 필드 검증 규칙 (docs/api.md 5.1)
 */
public final class AccountValidation {

    public static final String LOGIN_ID_PATTERN = "^[A-Za-z0-9._-]+$";
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$";
    public static final String PASSWORD_MESSAGE = "비밀번호는 8~64자이며 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.";
    public static final String PHONE_PATTERN = "^[0-9-]*$";

    private AccountValidation() {
    }
}
