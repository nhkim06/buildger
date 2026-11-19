package com.example.backend.project.entity;

import com.example.backend.account.entity.Account;
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
 * project 테이블 (create_table.sql 2. PROJECT). detail JSONB 구조는 {@link ProjectDetail}.
 */
@Entity
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "pname", nullable = false)
    private String pname;

    @Column(name = "detail", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private Account createdBy;

    public Project(String pname, String detail, Account createdBy) {
        this.pname = pname;
        this.detail = detail;
        this.createdBy = createdBy;
    }

    public UUID getOwnerUid() {
        return createdBy == null ? null : createdBy.getUid();
    }

    public void rename(String pname) {
        this.pname = pname;
    }

    public void replaceDetail(String detail) {
        this.detail = detail;
    }
}
