package com.example.backend.project.entity;

/**
 * 프로젝트 내 역할 (docs/api.md 2.2)
 */
public enum ProjectRole {
    OWNER,
    MANAGER,
    MEMBER,
    VIEWER;

    /** 프로젝트 관리 권한 (수정, 참여자 관리) */
    public boolean canManage() {
        return this == OWNER || this == MANAGER;
    }

    /** 기록 작성 권한 */
    public boolean canWrite() {
        return this != VIEWER;
    }
}
