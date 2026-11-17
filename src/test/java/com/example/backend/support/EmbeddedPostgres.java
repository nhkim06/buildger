package com.example.backend.support;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 테스트 JVM 전체에서 하나의 임베디드 PostgreSQL을 공유한다. (Docker 불필요)
 */
public final class EmbeddedPostgres {

    private static io.zonky.test.db.postgres.embedded.EmbeddedPostgres instance;

    private EmbeddedPostgres() {
    }

    public static synchronized String jdbcUrl() {
        if (instance == null) {
            try {
                instance = io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder()
                        .setLocaleConfig("locale", "C")
                        .setLocaleConfig("encoding", "UTF8")
                        .start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    instance.close();
                } catch (IOException ignored) {
                    // 테스트 종료 시점이므로 무시
                }
            }));
        }
        return instance.getJdbcUrl("postgres", "postgres");
    }
}
