package com.example.backend.ai.client;

import lombok.Getter;

/**
 * AI 서버 호출 실패. failure는 metadata.aiAnalyses[].errorCode로 저장된다.
 */
@Getter
public class AiClientException extends RuntimeException {

    private final Failure failure;

    public AiClientException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public enum Failure {
        AI_SERVER_ERROR("AI 서버가 오류를 반환했거나 연결할 수 없습니다."),
        AI_SERVER_TIMEOUT("AI 서버 응답 시간이 초과되었습니다."),
        AI_INVALID_RESPONSE("AI 서버 응답 형식이 올바르지 않습니다.");

        /** 외부에 노출해도 되는 고정 메시지 (내부 예외 메시지는 저장·응답하지 않는다) */
        private final String publicMessage;

        Failure(String publicMessage) {
            this.publicMessage = publicMessage;
        }

        public String publicMessage() {
            return publicMessage;
        }
    }
}
