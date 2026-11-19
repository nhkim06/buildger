package com.example.backend.project.entity;

import com.example.backend.global.jsonb.JsonbObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * project.detail.members[] 항목. OWNER는 project.created_by로 판단하므로 여기에 저장하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProjectMember extends JsonbObject {

    private String uid;
    private ProjectRole projectRole;
    private String addedBy;
    private String addedAt;
    private String updatedAt;

    public ProjectMember(String uid, ProjectRole projectRole, String addedBy, String addedAt) {
        this.uid = uid;
        this.projectRole = projectRole;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
        this.updatedAt = addedAt;
    }
}
