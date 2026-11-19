package com.example.backend.construction.entity;

import com.example.backend.global.jsonb.JsonbObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * data.metadata.aiAnalyses[] — 추가 전용 AI 분석 이력 (docs/api.md 3.3)
 */
@Getter
@Setter
@NoArgsConstructor
public class AiAnalysis extends JsonbObject {

    private String analysisId;
    private Integer version;
    private AiAnalysisStatus status;
    private String model;
    private List<Long> targetStorageIds = new ArrayList<>();
    private String summary;
    private Object result;
    private String errorCode;
    private String errorMessage;
    private String requestedBy;
    private String requestedAt;
    private String completedAt;
}
