package com.example.backend.ai;

import com.example.backend.ai.client.AiAnalysisClient.AiImage;
import com.example.backend.ai.client.AiAnalysisClient.AiRequest;
import com.example.backend.ai.client.AiAnalysisClient.AiResult;
import com.example.backend.ai.client.AiClientException;
import com.example.backend.ai.client.AiServerProperties;
import com.example.backend.ai.client.RestAiAnalysisClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 HTTP 서버(JDK HttpServer)를 상대로 AI 서버 호출·응답 검증·오류 분류를 확인한다.
 */
class RestAiAnalysisClientTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private RestAiAnalysisClient clientFor(int status, String body, long delayMillis, AtomicReference<String> received)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/analyze", exchange -> {
            if (received != null) {
                received.set(exchange.getRequestHeaders().getFirst("Content-Type") + "\n"
                        + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return new RestAiAnalysisClient(new AiServerProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2), Duration.ofMillis(500)));
    }

    private static AiRequest request() {
        return new AiRequest(101L, "PHOTO", "철근콘크리트공사",
                List.of(new AiImage(15L, "slab.png", "image/png", new byte[]{1, 2, 3})));
    }

    @Test
    void success_sendsMultipartAndParsesResult() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        RestAiAnalysisClient client = clientFor(200,
                "{\"model\":\"rebar-detector-v2\",\"summary\":\"ok\",\"result\":{\"rebarSpacingMm\":198}}", 0, received);

        AiResult result = client.analyze(request());

        assertThat(result.model()).isEqualTo("rebar-detector-v2");
        assertThat(result.summary()).isEqualTo("ok");
        assertThat(result.result()).isEqualTo(Map.of("rebarSpacingMm", 198));
        assertThat(received.get()).startsWith("multipart/form-data")
                .contains("name=\"images\"").contains("filename=\"slab.png\"")
                .contains("name=\"context\"").contains("\"dataId\":101");
    }

    @Test
    void serverError_isAiServerError() throws Exception {
        RestAiAnalysisClient client = clientFor(500, "{\"error\":\"boom\"}", 0, null);

        assertThatThrownBy(() -> client.analyze(request()))
                .isInstanceOf(AiClientException.class)
                .extracting(e -> ((AiClientException) e).getFailure())
                .isEqualTo(AiClientException.Failure.AI_SERVER_ERROR);
    }

    @Test
    void invalidResponse_isAiInvalidResponse() throws Exception {
        RestAiAnalysisClient client = clientFor(200, "{\"summary\":\"no model\"}", 0, null);

        assertThatThrownBy(() -> client.analyze(request()))
                .extracting(e -> ((AiClientException) e).getFailure())
                .isEqualTo(AiClientException.Failure.AI_INVALID_RESPONSE);
    }

    @Test
    void notJson_isAiInvalidResponse() throws Exception {
        RestAiAnalysisClient client = clientFor(200, "<html>oops</html>", 0, null);

        assertThatThrownBy(() -> client.analyze(request()))
                .extracting(e -> ((AiClientException) e).getFailure())
                .isEqualTo(AiClientException.Failure.AI_INVALID_RESPONSE);
    }

    @Test
    void slowResponse_isTimeout() throws Exception {
        RestAiAnalysisClient client = clientFor(200, "{\"model\":\"m\",\"result\":{}}", 2000, null);

        assertThatThrownBy(() -> client.analyze(request()))
                .extracting(e -> ((AiClientException) e).getFailure())
                .isEqualTo(AiClientException.Failure.AI_SERVER_TIMEOUT);
    }

    @Test
    void connectionRefused_isAiServerError() {
        RestAiAnalysisClient client = new RestAiAnalysisClient(new AiServerProperties(
                "http://127.0.0.1:9", Duration.ofSeconds(1), Duration.ofSeconds(1)));

        assertThatThrownBy(() -> client.analyze(request()))
                .extracting(e -> ((AiClientException) e).getFailure())
                .isEqualTo(AiClientException.Failure.AI_SERVER_ERROR);
    }
}
