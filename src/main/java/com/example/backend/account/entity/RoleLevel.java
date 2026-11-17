package com.example.backend.account.entity;

import lombok.Getter;

/**
 * account.role_level (1: 현장직, 2: 시공사, 3: 시행사)
 */
@Getter
public enum RoleLevel {

    FIELD_WORKER((short) 1),
    CONTRACTOR((short) 2),
    DEVELOPER((short) 3);

    private final short level;

    RoleLevel(short level) {
        this.level = level;
    }

    public static RoleLevel fromLevel(int level) {
        for (RoleLevel role : values()) {
            if (role.level == level) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role_level: " + level);
    }

    /** 프로젝트 생성·확인·서명이 가능한 역할 (시공사·시행사) */
    public boolean isManagerial() {
        return this == CONTRACTOR || this == DEVELOPER;
    }
}
