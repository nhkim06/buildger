package com.example.backend.construction.entity;

import com.example.backend.global.jsonb.Deletion;
import com.example.backend.global.jsonb.JsonbObject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * data.metadata.files[] — storage 행과 기록의 연결 및 이미지 메타데이터.
 */
@Getter
@Setter
@NoArgsConstructor
public class FileEntry extends JsonbObject {

    private Long storageId;
    private String originalName;
    private String contentType;
    private Long size;
    private String sha256;
    private Integer width;
    private Integer height;
    private String uploadedBy;
    private String uploadedAt;
    private Deletion deletion;

    @JsonIgnore
    public boolean isDeleted() {
        return deletion != null;
    }
}
