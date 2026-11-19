package com.example.backend.construction.entity;

/**
 * 기록의 최근 확인 상태 (confirmationStatus). 확인 항목 자체의 status는 APPROVED·REJECTED만 사용한다.
 */
public enum ConfirmationState {
    NONE,
    APPROVED,
    REJECTED
}
