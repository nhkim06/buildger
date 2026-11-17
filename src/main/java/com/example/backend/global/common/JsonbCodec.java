package com.example.backend.global.common;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSONB 컬럼(String)과 Java 모델 간 변환.
 * 전역 Jackson 설정 변경의 영향을 받지 않도록 별도 매퍼를 사용한다.
 */
@Component
public class JsonbCodec {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public <T> T read(String json, Class<T> type) {
        String source = (json == null || json.isBlank()) ? "{}" : json;
        return mapper.readValue(source, type);
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public JsonMapper mapper() {
        return mapper;
    }
}
