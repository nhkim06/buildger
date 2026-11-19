package com.example.backend.project.service;

import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.entity.Project;
import com.example.backend.project.entity.ProjectDetail;
import com.example.backend.project.entity.ProjectRole;

/**
 * 접근 권한이 검증된 프로젝트와 요청자의 프로젝트 역할.
 */
public record ProjectContext(Project project, ProjectDetail detail, AuthUser user, ProjectRole role) {

    public Long projectId() {
        return project.getProjectId();
    }

    public void requireManager() {
        if (!role.canManage()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PROJECT_ROLE);
        }
    }

    public void requireOwner() {
        if (role != ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PROJECT_ROLE);
        }
    }

    public void requireWriter() {
        if (!role.canWrite()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PROJECT_ROLE);
        }
    }
}
