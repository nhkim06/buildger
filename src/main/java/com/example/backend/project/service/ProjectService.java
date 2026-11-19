package com.example.backend.project.service;

import com.example.backend.account.entity.RoleLevel;
import com.example.backend.account.repository.AccountRepository;
import com.example.backend.global.common.PageResponse;
import com.example.backend.global.common.SqlLike;
import com.example.backend.global.common.Times;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.jsonb.Deletion;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.dto.ProjectCreateRequest;
import com.example.backend.project.dto.ProjectMapper;
import com.example.backend.project.dto.ProjectResponse;
import com.example.backend.project.dto.ProjectUpdateRequest;
import com.example.backend.project.entity.Project;
import com.example.backend.project.entity.ProjectDetail;
import com.example.backend.project.entity.ProjectRole;
import com.example.backend.project.entity.ProjectStatus;
import com.example.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;
    private final ProjectAccessService projectAccess;

    @Transactional
    public ProjectResponse create(AuthUser user, ProjectCreateRequest request) {
        if (!RoleLevel.fromLevel(user.roleLevel()).isManagerial()) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED, "프로젝트는 시공사·시행사 계정만 생성할 수 있습니다.");
        }
        validatePeriod(request.startDate(), request.endDate());

        String now = Times.nowString();
        ProjectDetail detail = new ProjectDetail();
        detail.setDescription(request.description());
        detail.setAddress(request.address());
        detail.setStartDate(toText(request.startDate()));
        detail.setEndDate(toText(request.endDate()));
        detail.setStatus(request.status() == null ? ProjectStatus.PLANNED : request.status());
        detail.setCreatedAt(now);
        detail.setUpdatedAt(now);

        Project project = new Project(request.name().strip(), null, accountRepository.getReferenceById(user.uid()));
        projectAccess.saveDetail(project, detail);
        projectRepository.save(project);
        return ProjectMapper.toResponse(project, detail, ProjectRole.OWNER);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(AuthUser user, String keyword, ProjectStatus status, int page, int size) {
        String memberJson = projectAccess.memberContainmentJson(user.uid());
        Page<Project> result = projectRepository.findAccessible(
                user.uid(), memberJson,
                keyword == null || keyword.isBlank() ? null : SqlLike.contains(keyword.strip()),
                status == null ? null : status.name(),
                PageRequest.of(page, size));
        List<ProjectResponse> content = result.getContent().stream()
                .map(project -> {
                    ProjectDetail detail = projectAccess.readDetail(project);
                    return ProjectMapper.toResponse(project, detail,
                            projectAccess.resolveRole(project, detail, user.uid()));
                })
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long projectId, AuthUser user) {
        ProjectContext ctx = projectAccess.read(projectId, user);
        return ProjectMapper.toResponse(ctx.project(), ctx.detail(), ctx.role());
    }

    @Transactional
    public ProjectResponse update(Long projectId, AuthUser user, ProjectUpdateRequest request) {
        if (request.hasNoChanges()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경할 필드가 없습니다.");
        }
        ProjectContext ctx = projectAccess.update(projectId, user);
        ctx.requireManager();

        ProjectDetail detail = ctx.detail();
        LocalDate start = request.startDate() != null ? request.startDate() : parse(detail.getStartDate());
        LocalDate end = request.endDate() != null ? request.endDate() : parse(detail.getEndDate());
        validatePeriod(start, end);

        if (request.name() != null) {
            ctx.project().rename(request.name().strip());
        }
        if (request.description() != null) {
            detail.setDescription(request.description());
        }
        if (request.address() != null) {
            detail.setAddress(request.address());
        }
        if (request.startDate() != null) {
            detail.setStartDate(toText(request.startDate()));
        }
        if (request.endDate() != null) {
            detail.setEndDate(toText(request.endDate()));
        }
        if (request.status() != null) {
            detail.setStatus(request.status());
        }
        detail.setUpdatedAt(Times.nowString());
        detail.setUpdatedBy(user.uid().toString());
        projectAccess.saveDetail(ctx.project(), detail);
        return ProjectMapper.toResponse(ctx.project(), detail, ctx.role());
    }

    /** Soft Delete: detail.deletion만 추가하고 하위 기록·이미지는 변경하지 않는다. (docs/api.md 7.5) */
    @Transactional
    public void delete(Long projectId, AuthUser user, String reason) {
        ProjectContext ctx = projectAccess.update(projectId, user);
        ctx.requireOwner();
        ProjectDetail detail = ctx.detail();
        detail.setDeletion(new Deletion(Times.nowString(), user.uid().toString(),
                reason == null || reason.isBlank() ? null : reason.strip()));
        projectAccess.saveDetail(ctx.project(), detail);
    }

    private static void validatePeriod(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "준공 예정일은 착공일 이후여야 합니다.");
        }
    }

    private static String toText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static LocalDate parse(String date) {
        return date == null ? null : LocalDate.parse(date);
    }
}
