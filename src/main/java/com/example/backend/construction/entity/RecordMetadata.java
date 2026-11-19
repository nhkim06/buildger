package com.example.backend.construction.entity;

import com.example.backend.global.jsonb.Deletion;
import com.example.backend.global.jsonb.JsonbObject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * data.metadata JSONB (docs/api.md 3.3)
 */
@Getter
@Setter
public class RecordMetadata extends JsonbObject {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_ACTIVE_FILES = 20;
    public static final int MAX_TOTAL_FILES = 30;
    public static final int MAX_CONFIRMATIONS = 50;
    public static final int MAX_AI_ANALYSES = 30;

    private Integer schemaVersion = CURRENT_SCHEMA_VERSION;

    private String title;
    private String description;
    private String process;
    private String capturedAt;
    private Location location;

    private String createdAt;
    private String updatedAt;
    private String updatedBy;

    private List<FileEntry> files = new ArrayList<>();
    private List<Confirmation> confirmations = new ArrayList<>();
    private List<AiAnalysis> aiAnalyses = new ArrayList<>();

    private Correction correction;
    private Deletion deletion;

    public List<FileEntry> getFiles() {
        if (files == null) {
            files = new ArrayList<>();
        }
        return files;
    }

    public List<Confirmation> getConfirmations() {
        if (confirmations == null) {
            confirmations = new ArrayList<>();
        }
        return confirmations;
    }

    public List<AiAnalysis> getAiAnalyses() {
        if (aiAnalyses == null) {
            aiAnalyses = new ArrayList<>();
        }
        return aiAnalyses;
    }

    @JsonIgnore
    public boolean isDeleted() {
        return deletion != null;
    }

    /** 확인·서명이 1건 이상이면 본문 변경이 불가능하다. */
    @JsonIgnore
    public boolean isLocked() {
        return !getConfirmations().isEmpty();
    }

    @JsonIgnore
    public List<FileEntry> getActiveFiles() {
        return getFiles().stream().filter(f -> !f.isDeleted()).toList();
    }

    public Optional<FileEntry> findActiveFile(Long storageId) {
        return getActiveFiles().stream().filter(f -> storageId.equals(f.getStorageId())).findFirst();
    }

    @JsonIgnore
    public ConfirmationState getConfirmationState() {
        List<Confirmation> list = getConfirmations();
        if (list.isEmpty()) {
            return ConfirmationState.NONE;
        }
        return list.get(list.size() - 1).getStatus();
    }

    @JsonIgnore
    public Optional<AiAnalysis> getLatestAiAnalysis() {
        return getAiAnalyses().stream()
                .filter(a -> a.getVersion() != null)
                .max((a, b) -> Integer.compare(a.getVersion(), b.getVersion()));
    }
}
