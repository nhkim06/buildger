package com.example.backend.project.repository;

import com.example.backend.project.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** SELECT ... FOR UPDATE (docs/api.md 4.5) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.projectId = :projectId")
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);

    /** SELECT ... FOR SHARE (docs/api.md 4.5) */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Project p where p.projectId = :projectId")
    Optional<Project> findByIdForShare(@Param("projectId") Long projectId);

    /**
     * 요청자가 OWNER이거나 detail.members에 포함된, 삭제되지 않은 프로젝트 (docs/api.md 7.2)
     */
    @Query(value = """
            SELECT p.* FROM project p
            WHERE (p.created_by = :uid OR p.detail->'members' @> CAST(:memberJson AS jsonb))
              AND p.detail->'deletion' IS NULL
              AND (CAST(:keyword AS text) IS NULL OR p.pname ILIKE CAST(:keyword AS text) ESCAPE '\\')
              AND (CAST(:status AS text) IS NULL OR COALESCE(p.detail->>'status', 'PLANNED') = CAST(:status AS text))
            ORDER BY p.project_id DESC
            """,
            countQuery = """
            SELECT count(*) FROM project p
            WHERE (p.created_by = :uid OR p.detail->'members' @> CAST(:memberJson AS jsonb))
              AND p.detail->'deletion' IS NULL
              AND (CAST(:keyword AS text) IS NULL OR p.pname ILIKE CAST(:keyword AS text) ESCAPE '\\')
              AND (CAST(:status AS text) IS NULL OR COALESCE(p.detail->>'status', 'PLANNED') = CAST(:status AS text))
            """,
            nativeQuery = true)
    Page<Project> findAccessible(@Param("uid") UUID uid,
                                 @Param("memberJson") String memberJson,
                                 @Param("keyword") String keyword,
                                 @Param("status") String status,
                                 Pageable pageable);
}
