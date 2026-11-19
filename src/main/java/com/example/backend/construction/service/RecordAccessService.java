package com.example.backend.construction.service;

import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.construction.entity.RecordType;
import com.example.backend.construction.repository.RecordRepository;
import com.example.backend.global.common.JsonbCodec;
import com.example.backend.global.error.BusinessException;
import com.example.backend.global.error.ErrorCode;
import com.example.backend.project.entity.ProjectRole;
import com.example.backend.project.service.ProjectContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 시공 기록 조회·잠금과 공통 권한 규칙 (docs/api.md 2.3, 4.5).
 * 잠금 조회는 반드시 project 잠금 이후, 트랜잭션 안에서 해당 행을 처음 읽을 때 사용한다.
 */
@Service
@RequiredArgsConstructor
public class RecordAccessService {

    private final RecordRepository recordRepository;
    private final JsonbCodec jsonbCodec;

    public RecordContext read(ProjectContext project, Long dataId) {
        return toContext(project, recordRepository.findById(dataId));
    }

    /** data 행 FOR UPDATE */
    public RecordContext lock(ProjectContext project, Long dataId) {
        return toContext(project, recordRepository.findByIdForUpdate(dataId));
    }

    public RecordMetadata readMetadata(ConstructionRecord record) {
        return jsonbCodec.read(record.getMetadata(), RecordMetadata.class);
    }

    public void saveMetadata(ConstructionRecord record, RecordMetadata metadata) {
        record.replaceMetadata(jsonbCodec.write(metadata));
    }

    /** 서명 전 기록 수정·삭제, 이미지 업로드·삭제 권한: OWNER·MANAGER 또는 작성자 본인(MEMBER) */
    public void requireModifiable(RecordContext ctx) {
        ProjectRole role = ctx.project().role();
        if (role.canManage()) {
            return;
        }
        if (role == ProjectRole.MEMBER && ctx.isAuthor()) {
            return;
        }
        throw new BusinessException(ErrorCode.INSUFFICIENT_PROJECT_ROLE);
    }

    public void requireNotLocked(RecordContext ctx) {
        if (ctx.metadata().isLocked()) {
            throw new BusinessException(ErrorCode.RECORD_LOCKED);
        }
    }

    public void requireNotInvalidationType(RecordContext ctx) {
        if (ctx.record().getRecordType() == RecordType.INVALIDATION) {
            throw new BusinessException(ErrorCode.INVALID_RECORD_TYPE);
        }
    }

    private RecordContext toContext(ProjectContext project, Optional<ConstructionRecord> found) {
        ConstructionRecord record = found
                .filter(r -> r.getProjectId().equals(project.projectId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        RecordMetadata metadata = readMetadata(record);
        if (metadata.isDeleted()) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return new RecordContext(project, record, metadata);
    }
}
