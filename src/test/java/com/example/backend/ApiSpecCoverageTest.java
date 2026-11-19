package com.example.backend;

import com.example.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * docs/api.md "API 요약" 표의 모든 API(Method + URL)가 실제로 구현되어 OpenAPI 문서에 노출되는지 검증한다.
 */
class ApiSpecCoverageTest extends IntegrationTest {

    private static final Pattern SUMMARY_ROW =
            Pattern.compile("^\\|\\s*\\d+\\.\\d+\\s*\\|[^|]*\\|\\s*(GET|POST|PATCH|PUT|DELETE)\\s*\\|\\s*`([^`]+)`\\s*\\|");

    @Test
    void everyApiInSpecIsImplemented() throws Exception {
        Set<String> specified = new TreeSet<>();
        for (String line : Files.readAllLines(Path.of("docs/api.md"), StandardCharsets.UTF_8)) {
            Matcher m = SUMMARY_ROW.matcher(line);
            if (m.find()) {
                specified.add(m.group(1) + " " + m.group(2));
            }
        }

        JsonNode docs = body(mockMvc.perform(get("/v3/api-docs")).andReturn());
        Set<String> implemented = new TreeSet<>();
        docs.get("paths").properties().forEach(path -> path.getValue().properties()
                .forEach(op -> implemented.add(op.getKey().toUpperCase() + " " + path.getKey())));

        assertThat(specified).hasSize(32);
        assertThat(implemented).containsAll(specified);
        assertThat(implemented).hasSameSizeAs(specified);
    }
}
