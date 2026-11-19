package com.example.backend.construction.entity;

import com.example.backend.global.jsonb.JsonbObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * data.metadata.confirmations[] — 추가 전용 확인·서명 이력 (docs/api.md 3.3)
 */
@Getter
@Setter
@NoArgsConstructor
public class Confirmation extends JsonbObject {

    private String confirmationId;
    private String confirmerUid;
    private String confirmerName;
    private String confirmerOrganization;
    private Integer confirmerRoleLevel;
    private ConfirmationState status;
    private String comment;
    private SignatureEntry signature;
    private Integer snapshotVersion;
    private String recordSnapshotSha256;
    private String confirmedAt;
}
