package com.example.backend.global.common;

/**
 * LIKE 검색어 이스케이프 (docs/api.md 9.2). SQL에서는 {@code ESCAPE '\'}와 함께 사용한다.
 */
public final class SqlLike {

    private SqlLike() {
    }

    public static String contains(String keyword) {
        return "%" + escape(keyword) + "%";
    }

    public static String exact(String keyword) {
        return escape(keyword);
    }

    private static String escape(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
