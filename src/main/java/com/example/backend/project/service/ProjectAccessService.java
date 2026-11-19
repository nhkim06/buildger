package com.example.backend.project.service;

import com.example.backend.global.common.JsonbCodec;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.entity.Project;
import com.example.backend.project.entity.ProjectDetail;
import com.example.backend.project.entity.ProjectMember;
import com.example.backend.project.entity.ProjectRole;
import com.example.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 프로젝트 조회 + 참여자 권한 검증 (docs/api.md 2장).
 * 존재하지 않거나 삭제된 프로젝트는 404, 참여자가 아니면 403.
 * 잠금이 필요한 쓰기 작업은 트랜잭션 안에서 이 클래스로 프로젝트 행을 처음 읽어야 한다. (docs/api.md 4.5)
 */
@Service
@RequiredArgsConstructor
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final JsonbCodec jsonbCodec;

    public ProjectContext read(Long projectId, AuthUser user) {
        return toContext(projectRepository.findById(projectId), user);
    }

    /** project 행 FOR SHARE: 하위 기록 쓰기와 프로젝트 삭제를 직렬화한다. */
    public ProjectContext share(Long projectId, AuthUser user) {
        return toContext(projectRepository.findByIdForShare(projectId), user);
    }

    /** project 행 FOR UPDATE: detail(JSONB) 변경 시 사용한다. */
    public ProjectContext update(Long projectId, AuthUser user) {
        return toContext(projectRepository.findByIdForUpdate(projectId), user);
    }

    public ProjectDetail readDetail(Project project) {
        return jsonbCodec.read(project.getDetail(), ProjectDetail.class);
    }

    public void saveDetail(Project project, ProjectDetail detail) {
        project.replaceDetail(jsonbCodec.write(detail));
    }

    /** detail.members 포함 검사용 JSON: [{"uid":"..."}] */
    public String memberContainmentJson(UUID uid) {
        return jsonbCodec.write(List.of(Map.of("uid", uid.toString())));
    }

    public ProjectRole resolveRole(Project project, ProjectDetail detail, UUID uid) {
        if (uid.equals(project.getOwnerUid())) {
            return ProjectRole.OWNER;
        }
        return detail.findMember(uid).map(ProjectMember::getProjectRole).orElse(null);
    }

    private ProjectContext toContext(Optional<Project> found, AuthUser user) {
        Project project = found.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        ProjectDetail detail = readDetail(project);
        if (detail.isDeleted()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        ProjectRole role = resolveRole(project, detail, user.uid());
        if (role == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }
        return new ProjectContext(project, detail, user, role);
    }
}
