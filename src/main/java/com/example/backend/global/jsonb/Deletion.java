package com.example.backend.global.jsonb;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Soft Delete 정보 (docs/api.md 3.1, 3.3). 삭제된 경우에만 키가 존재한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Deletion extends JsonbObject {

    private String deletedAt;
    private String deletedBy;
    private String reason;

    public Deletion(String deletedAt, String deletedBy, String reason) {
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.reason = reason;
    }
}
