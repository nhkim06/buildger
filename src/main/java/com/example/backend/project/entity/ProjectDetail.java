package com.example.backend.project.entity;

import com.example.backend.global.jsonb.Deletion;
import com.example.backend.global.jsonb.JsonbObject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * project.detail JSONB (docs/api.md 3.1)
 */
@Getter
@Setter
public class ProjectDetail extends JsonbObject {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_MEMBERS = 200;

    private Integer schemaVersion = CURRENT_SCHEMA_VERSION;
    private String description;
    private String address;
    private String startDate;
    private String endDate;
    private ProjectStatus status;
    private String createdAt;
    private String updatedAt;
    private String updatedBy;
    private List<ProjectMember> members = new ArrayList<>();
    private Deletion deletion;

    public List<ProjectMember> getMembers() {
        if (members == null) {
            members = new ArrayList<>();
        }
        return members;
    }

    public ProjectStatus getStatus() {
        return status == null ? ProjectStatus.PLANNED : status;
    }

    @JsonIgnore
    public boolean isDeleted() {
        return deletion != null;
    }

    public Optional<ProjectMember> findMember(UUID uid) {
        String key = uid.toString();
        return getMembers().stream().filter(m -> key.equalsIgnoreCase(m.getUid())).findFirst();
    }
}
