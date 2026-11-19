package com.example.backend.construction.entity;

import com.example.backend.global.jsonb.JsonbObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * data.metadata.correction — 정정·무효화 기록에만 존재 (docs/api.md 10장)
 */
@Getter
@Setter
@NoArgsConstructor
public class Correction extends JsonbObject {

    private CorrectionKind kind;
    private Long originalDataId;
    private Long rootDataId;
    private String reason;
    private String correctedBy;
    private String correctedAt;
}
