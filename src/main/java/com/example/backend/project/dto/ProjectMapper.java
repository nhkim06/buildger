package com.example.backend.project.dto;

import com.example.backend.account.dto.AccountSummary;
import com.example.backend.project.entity.Project;
import com.example.backend.project.entity.ProjectDetail;
import com.example.backend.project.entity.ProjectRole;

public final class ProjectMapper {

    private ProjectMapper() {
    }

    public static ProjectResponse toResponse(Project project, ProjectDetail detail, ProjectRole myRole) {
        int memberCount = detail.getMembers().size() + (project.getCreatedBy() == null ? 0 : 1);
        return new ProjectResponse(
                project.getProjectId(),
                project.getPname(),
                detail.getDescription(),
                detail.getAddress(),
                detail.getStartDate(),
                detail.getEndDate(),
                detail.getStatus(),
                AccountSummary.from(project.getCreatedBy()),
                myRole,
                memberCount,
                detail.getCreatedAt(),
                detail.getUpdatedAt());
    }
}
