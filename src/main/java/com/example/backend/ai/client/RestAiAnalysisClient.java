package com.example.backend.ai.client;

import com.example.backend.ai.client.AiClientException.Failure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 서버 동기 호출 (docs/api.md 13.0, 가정한 형식).
 * 요청: POST {base-url}/analyze, multipart(images[], context JSON)
 * 응답: {"model": string, "summary": string, "result": object}
 */
@Slf4j
@Component
public class RestAiAnalysisClient implements AiAnalysisClient {

    static final int MAX_RESULT_BYTES = 256 * 1024;

    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public RestAiAnalysisClient(AiServerProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public AiResult analyze(AiRequest request) {
        String body;
        try {
            body = restClient.post()
                    .uri("/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart(request))
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            Failure failure = isTimeout(e) ? Failure.AI_SERVER_TIMEOUT : Failure.AI_SERVER_ERROR;
            log.warn("AI server call failed: dataId={}, failure={}", request.dataId(), failure);
            throw new AiClientException(failure, failure.publicMessage(), e);
        } catch (RestClientException e) {
            log.warn("AI server returned error: dataId={}", request.dataId());
            throw new AiClientException(Failure.AI_SERVER_ERROR, Failure.AI_SERVER_ERROR.publicMessage(), e);
        }
        return parse(body);
    }

    private MultiValueMap<String, Object> multipart(AiRequest request) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (AiImage image : request.images()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(image.contentType()));
            ByteArrayResource resource = new ByteArrayResource(image.bytes()) {
                @Override
                public String getFilename() {
                    return image.fileName();
                }
            };
            parts.add("images", new HttpEntity<>(resource, headers));
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("dataId", request.dataId());
        context.put("type", request.recordType());
        context.put("process", request.process());
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        parts.add("context", new HttpEntity<>(jsonMapper.writeValueAsString(context), jsonHeaders));
        return parts;
    }

    AiResult parse(String body) {
        JsonNode root;
        try {
            root = body == null ? null : jsonMapper.readTree(body);
        } catch (JacksonException e) {
            throw invalid(e);
        }
        if (root == null || !root.isObject()) {
            throw invalid(null);
        }
        JsonNode model = root.get("model");
        JsonNode result = root.get("result");
        JsonNode summary = root.get("summary");
        if (model == null || !model.isString() || model.asString().isBlank()
                || model.asString().length() > 100 || result == null || !result.isObject()) {
            throw invalid(null);
        }
        Object resultValue = jsonMapper.convertValue(result, Object.class);
        if (jsonMapper.writeValueAsString(resultValue).getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_BYTES) {
            throw invalid(null);
        }
        String summaryText = summary == null || summary.isNull() ? null : summary.asString();
        if (summaryText != null && summaryText.length() > 2000) {
            summaryText = summaryText.substring(0, 2000);
        }
        return new AiResult(model.asString(), summaryText, resultValue);
    }

    private static AiClientException invalid(Throwable cause) {
        return new AiClientException(Failure.AI_INVALID_RESPONSE, Failure.AI_INVALID_RESPONSE.publicMessage(), cause);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable cur = e; cur != null; cur = cur.getCause()) {
            if (cur instanceof HttpTimeoutException || cur instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
