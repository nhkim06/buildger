package com.example.backend.construction.entity;

import com.example.backend.account.entity.Account;
import com.example.backend.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

/**
 * data 테이블 (create_table.sql 3. DATA). 시공 기록 1건 = data 1행.
 * metadata JSONB 구조는 {@link RecordMetadata}.
 */
@Entity
@Table(name = "data")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConstructionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "data_id", nullable = false, updatable = false)
    private Long dataId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", updatable = false)
    private Account uploadedBy;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String metadata;

    @Column(name = "url", columnDefinition = "text")
    private String url;

    public ConstructionRecord(Project project, Account uploadedBy, RecordType type, String metadata, String url) {
        this.project = project;
        this.uploadedBy = uploadedBy;
        this.type = type.name();
        this.metadata = metadata;
        this.url = url;
    }

    public Long getProjectId() {
        return project.getProjectId();
    }

    public UUID getUploadedByUid() {
        return uploadedBy == null ? null : uploadedBy.getUid();
    }

    /** API 외부에서 입력된 알 수 없는 type이면 null */
    public RecordType getRecordType() {
        return RecordType.parse(type);
    }

    public void changeType(RecordType type) {
        this.type = type.name();
    }

    public void changeUrl(String url) {
        this.url = url;
    }

    public void replaceMetadata(String metadata) {
        this.metadata = metadata;
    }
}
