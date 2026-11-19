package com.example.backend.ai.client;

import java.util.List;

/**
 * AI 서버 호출 계약 (docs/api.md 13.0).
 * AI 서버의 실제 요청·응답 형식은 구현체 안에서만 변환하므로, 형식이 바뀌거나
 * 향후 비동기(서비스 토큰 콜백) 방식으로 바뀌어도 API 명세와 Service는 영향을 받지 않는다.
 */
public interface AiAnalysisClient {

    /**
     * @throws AiClientException AI 서버 오류·시간 초과·잘못된 응답
     */
    AiResult analyze(AiRequest request);

    record AiRequest(Long dataId, String recordType, String process, List<AiImage> images) {
    }

    record AiImage(Long storageId, String fileName, String contentType, byte[] bytes) {
    }

    record AiResult(String model, String summary, Object result) {
    }
}
