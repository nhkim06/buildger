package com.example.backend.construction.service;

import com.example.backend.construction.entity.ConstructionRecord;
import com.example.backend.construction.entity.RecordMetadata;
import com.example.backend.project.service.ProjectContext;

/**
 * 접근이 검증된 시공 기록과 metadata 모델.
 */
public record RecordContext(ProjectContext project, ConstructionRecord record, RecordMetadata metadata) {

    public Long dataId() {
        return record.getDataId();
    }

    public boolean isAuthor() {
        return project.user().uid().equals(record.getUploadedByUid());
    }
}
