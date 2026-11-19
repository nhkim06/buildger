package com.example.backend.construction.entity;

/**
 * data.type 허용 값 (docs/api.md 3.2). INVALIDATION은 무효화 API로만 생성한다.
 */
public enum RecordType {
    PHOTO,
    INSPECTION,
    NOTE,
    INVALIDATION;

    public static RecordType parse(String value) {
        if (value == null) {
            return null;
        }
        for (RecordType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
