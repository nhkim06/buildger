package com.example.backend.construction.entity;

import com.example.backend.global.jsonb.JsonbObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * data.metadata.confirmations[].signature — 서명 이미지 (storage 저장)
 */
@Getter
@Setter
@NoArgsConstructor
public class SignatureEntry extends JsonbObject {

    private Long storageId;
    private String contentType;
    private Long size;
    private String sha256;
}
