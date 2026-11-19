package com.example.backend.project.service;

import com.example.backend.account.entity.Account;
import com.example.backend.account.repository.AccountRepository;
import com.example.backend.global.common.Times;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.global.security.AuthUser;
import com.example.backend.project.dto.MemberAddRequest;
import com.example.backend.project.dto.MemberRoleUpdateRequest;
import com.example.backend.project.dto.ProjectMemberResponse;
import com.example.backend.project.entity.ProjectDetail;
import com.example.backend.project.entity.ProjectMember;
import com.example.backend.project.entity.ProjectRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 프로젝트 참여자 관리 (docs/api.md 8장).
 * detail.members 변경은 project 행 FOR UPDATE 잠금 후 중복·권한을 검사하고 같은 트랜잭션에서 저장한다. (docs/api.md 4.3)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectAccessService projectAccess;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> list(Long projectId, AuthUser user) {
        ProjectContext ctx = projectAccess.read(projectId, user);
        ProjectDetail detail = ctx.detail();

        List<UUID> memberUids = new ArrayList<>();
        for (ProjectMember member : detail.getMembers()) {
            parseUid(member.getUid()).ifPresent(memberUids::add);
        }
        Map<UUID, Account> accounts = accountRepository.findAllById(memberUids).stream()
                .collect(Collectors.toMap(Account::getUid, Function.identity()));

        List<ProjectMemberResponse> result = new ArrayList<>();
        Account owner = ctx.project().getCreatedBy();
        if (owner != null) {
            result.add(ProjectMemberResponse.of(owner, ProjectRole.OWNER, detail.getCreatedAt()));
        }
        detail.getMembers().stream()
                .sorted(Comparator.comparing(m -> m.getAddedAt() == null ? "" : m.getAddedAt()))
                .forEach(member -> {
                    Account account = parseUid(member.getUid()).map(accounts::get).orElse(null);
                    if (account == null) {
                        // FK가 없어 생길 수 있는 깨진 참조는 응답에서 제외한다. (docs/api.md 4.2)
                        log.warn("Broken member reference: projectId={}", ctx.projectId());
                        return;
                    }
                    result.add(ProjectMemberResponse.of(account, member.getProjectRole(), member.getAddedAt()));
                });
        return result;
    }

    @Transactional
    public ProjectMemberResponse add(Long projectId, AuthUser user, MemberAddRequest request) {
        ProjectContext ctx = projectAccess.update(projectId, user);
        ctx.requireManager();
        validateAssignableRole(ctx, request.projectRole());

        Account target = accountRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        ProjectDetail detail = ctx.detail();
        if (target.getUid().equals(ctx.project().getOwnerUid()) || detail.findMember(target.getUid()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_PROJECT_MEMBER);
        }
        if (detail.getMembers().size() >= ProjectDetail.MAX_MEMBERS) {
            throw new BusinessException(ErrorCode.MEMBER_LIMIT_EXCEEDED);
        }

        String now = Times.nowString();
        ProjectMember member = new ProjectMember(target.getUid().toString(), request.projectRole(),
                user.uid().toString(), now);
        detail.getMembers().add(member);
        projectAccess.saveDetail(ctx.project(), detail);
        return ProjectMemberResponse.of(target, member.getProjectRole(), member.getAddedAt());
    }

    @Transactional
    public ProjectMemberResponse changeRole(Long projectId, UUID targetUid, AuthUser user,
                                            MemberRoleUpdateRequest request) {
        ProjectContext ctx = projectAccess.update(projectId, user);
        ctx.requireManager();
        validateAssignableRole(ctx, request.projectRole());
        if (targetUid.equals(ctx.project().getOwnerUid())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_OWNER);
        }
        ProjectDetail detail = ctx.detail();
        ProjectMember member = detail.findMember(targetUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (ctx.role() == ProjectRole.MANAGER && member.getProjectRole() == ProjectRole.MANAGER) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PROJECT_ROLE, "MANAGER는 다른 MANAGER의 역할을 변경할 수 없습니다.");
        }
        Account account = accountRepository.findById(targetUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        member.setProjectRole(request.projectRole());
        member.setUpdatedAt(Times.nowString());
        projectAccess.saveDetail(ctx.project(), detail);
        return ProjectMemberResponse.of(account, member.getProjectRole(), member.getAddedAt());
    }

    @Transactional
    public void remove(Long projectId, UUID targetUid, AuthUser user) {
        ProjectContext ctx = projectAccess.update(projectId, user);
        boolean self = targetUid.equals(user.uid());
        if (!self) {
            ctx.requireManager();
        }
        if (targetUid.equals(ctx.project().getOwnerUid())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_OWNER);
        }
        ProjectDetail detail = ctx.detail();
        ProjectMember member = detail.findMember(targetUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!self && ctx.role() == ProjectRole.MANAGER && member.getProjectRole() == ProjectRole.MANAGER) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PROJECT_ROLE, "MANAGER는 다른 MANAGER를 제외할 수 없습니다.");
        }
        detail.getMembers().remove(member);
        projectAccess.saveDetail(ctx.project(), detail);
    }

    private void validateAssignableRole(ProjectContext ctx, ProjectRole role) {
        if (role == ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "OWNER 역할은 지정할 수 없습니다.");
        }
        if (role == ProjectRole.MANAGER && ctx.role() != ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PROJECT_ROLE, "MANAGER는 OWNER만 지정할 수 있습니다.");
        }
    }

    private static Optional<UUID> parseUid(String uid) {
        try {
            return Optional.of(UUID.fromString(uid));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
