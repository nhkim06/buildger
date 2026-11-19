package com.example.backend.construction.repository;

import com.example.backend.construction.entity.ConstructionRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecordRepository extends JpaRepository<ConstructionRecord, Long> {

    /** SELECT ... FOR UPDATE (docs/api.md 4.5) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ConstructionRecord r where r.dataId = :dataId")
    Optional<ConstructionRecord> findByIdForUpdate(@Param("dataId") Long dataId);

    /**
     * 주어진 기록을 originalDataId로 참조하는, 삭제되지 않은 정정·무효화 기록.
     * 결과: [originalDataId, kind, dataId]
     */
    @Query(value = """
            SELECT CAST(c.metadata->'correction'->>'originalDataId' AS bigint) AS original_id,
                   c.metadata->'correction'->>'kind' AS kind,
                   c.data_id AS data_id
            FROM data c
            WHERE c.metadata->'deletion' IS NULL
              AND c.metadata->'correction'->>'originalDataId' IN (:originalIds)
            ORDER BY c.data_id
            """, nativeQuery = true)
    List<Object[]> findCorrectionLinks(@Param("originalIds") Collection<String> originalIds);

    /** 정정 체인(rootDataId 기준)의 삭제되지 않은 기록 (docs/api.md 10.3) */
    @Query(value = """
            SELECT d.* FROM data d
            WHERE d.metadata->'deletion' IS NULL
              AND (d.data_id = :rootId OR d.metadata->'correction'->>'rootDataId' = CAST(:rootId AS text))
            ORDER BY d.data_id
            """, nativeQuery = true)
    List<ConstructionRecord> findChain(@Param("rootId") Long rootId);

    /**
     * storageId가 첨부파일 또는 서명 이미지로 연결된 기록 (docs/api.md 11.3).
     */
    @Query(value = """
            SELECT d.* FROM data d
            WHERE d.metadata->'files' @> CAST(:fileJson AS jsonb)
               OR d.metadata->'confirmations' @> CAST(:signatureJson AS jsonb)
            """, nativeQuery = true)
    List<ConstructionRecord> findByStorageReference(@Param("fileJson") String fileJson,
                                                    @Param("signatureJson") String signatureJson);
}
