package com.example.backend.global.jsonb;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSONB 모델 공통 상위 클래스 (docs/api.md 3.0).
 * - 값이 null인 키는 저장하지 않는다. (deletion, correction 등은 해당 상태일 때만 키가 존재)
 * - 모델에 정의되지 않은 키는 읽은 그대로 보존해 다시 저장한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class JsonbObject {

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void putUnknownField(String key, Object value) {
        unknownFields.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getUnknownFields() {
        return unknownFields;
    }
}
