# API 개발 기록

`docs/api.md` 명세를 기준으로 단계별 구현 결과를 기록한다.

- 기준 스키마: `src/main/resources/sql/create_table.sql` — **변경하지 않음**
- 프레임워크: Spring Boot 4.1.2 (Spring Framework 7, Spring Security 7, Hibernate 7.4, Jackson 3), Java 21

## 공통 사항

### 추가한 의존성

| 의존성 | 용도 |
|---|---|
| `spring-boot-starter-security` | 인증·인가, `PasswordEncoder`(BCrypt) |
| `spring-boot-starter-oauth2-resource-server` | JWT 발급·검증 (Spring Security 내장 Nimbus 사용, 별도 JWT 라이브러리 미사용) |
| `springdoc-openapi-starter-webmvc-ui:3.2.0` | Swagger UI |
| (test) `spring-boot-starter-security-test` | 보안 테스트 |
| (test) `io.zonky.test:embedded-postgres:2.2.2` | Docker 없이 실행되는 테스트용 PostgreSQL |

### 설정 (`application.properties`)

| 설정 | 값 | 비고 |
|---|---|---|
| `jwt.secret` | `${JWT_SECRET}` | **실행 시 환경변수 필요** (32바이트 이상, 미설정 시 기동 실패) |
| `jwt.access-token-expiration` | `7200s` | 2시간 |
| `spring.datasource.hikari.connection-init-sql` | `SET lock_timeout = '3s'` | 행 잠금 대기 3초 (명세 4.5) |
| `spring.jpa.open-in-view` | `false` | 트랜잭션 밖 지연 로딩 방지 |
| `spring.servlet.multipart.*` | 10MB / 100MB | 명세 11.0 |
| `ai.server.*` | `${AI_SERVER_URL:http://localhost:8000}`, 5s, 60s | 명세 13.0 |

### 테스트 방식

- 모든 API 테스트는 `@SpringBootTest` + MockMvc 통합 테스트로, **임베디드 PostgreSQL에 실제 `create_table.sql`을 적용**해 실행한다. JSONB 연산자, 행 잠금, 제약조건을 실제 PostgreSQL에서 검증한다.
- `ddl-auto=validate`로 컨텍스트가 로딩되므로, 테스트가 통과하면 Entity와 실제 스키마의 컬럼·타입이 일치함이 검증된다.
- 테스트마다 4개 테이블을 `TRUNCATE`해 서로 독립적으로 실행된다.
- `.env`의 원격 DB(Railway)에는 테스트를 실행하지 않았다. (공유 DB에 테스트 데이터가 쓰이는 것을 방지)

### 공통 구현

| 구성 요소 | 파일 | 내용 |
|---|---|---|
| 오류 코드 | `global/error/ErrorCode.java` | 명세 15장의 코드와 HTTP 상태 (+ `ACCESS_DENIED`, `RESOURCE_NOT_FOUND`) |
| 전역 예외 처리 | `global/error/GlobalExceptionHandler.java` | `@RestControllerAdvice`. Bean Validation, JSON 파싱, 업로드 크기, 잠금 실패(`CONCURRENT_MODIFICATION`), 예상치 못한 예외(500, 내부 정보 미노출) |
| 오류 응답 | `global/error/ErrorResponse.java` | `timestamp, status, code, message, path, errors` |
| 보안 설정 | `global/config/SecurityConfig.java` | Stateless, `/api/auth/**`·Swagger 공개, HS256 JWT |
| 토큰 발급 | `global/security/JwtTokenProvider.java` | `sub`(uid), `role`, `iat`, `exp` |
| 토큰 검증 | `global/security/AccountJwtAuthenticationConverter.java` | 요청마다 uid로 계정을 조회해 `AuthUser` 생성, 계정이 없으면 401 |
| 만료 구분 | `global/security/ExpiryAwareJwtDecoder.java` | 만료 토큰을 `TOKEN_EXPIRED`로 구분 |
| 401·403 응답 | `global/security/RestSecurityErrorHandler.java` | 보안 필터 단계 오류도 공통 형식으로 응답 |
| Swagger | `global/config/OpenApiConfig.java` | `bearerAuth` 보안 스키마 → Swagger UI `Authorize` 버튼 |
| 시각 | `global/common/Times.java` | UTC `yyyy-MM-dd'T'HH:mm:ss'Z'`, KST 날짜 경계 변환 |
| JSONB 변환 | `global/common/JsonbCodec.java` | JSONB(String) ↔ Java 모델 (전역 Jackson 설정과 분리된 매퍼) |

---

## 1단계. 인증·계정 API

**상태: 완료**

| # | API | Method / URL | 상태 |
|---|---|---|---|
| 5.1 | 회원가입 | `POST /api/auth/signup` | ✅ 구현 |
| 5.2 | 로그인 | `POST /api/auth/login` | ✅ 구현 |
| 6.1 | 내 계정 조회 | `GET /api/accounts/me` | ✅ 구현 |
| 6.2 | 내 계정 수정 | `PATCH /api/accounts/me` | ✅ 구현 |
| 6.3 | 비밀번호 변경 | `PATCH /api/accounts/me/password` | ✅ 구현 |

### 주요 구현 내용

| 파일 | 내용 |
|---|---|
| `account/entity/Account.java` | `account` 테이블 매핑. `role_level`은 `Short`(SMALLINT), uid는 UUID |
| `account/entity/RoleLevel.java` | 1·2·3 ↔ FIELD_WORKER·CONTRACTOR·DEVELOPER |
| `account/service/AuthService.java` | 가입: loginId 사전 중복 조회 + `saveAndFlush`의 UNIQUE 위반도 409로 변환 (동시 가입 대응), 비밀번호 BCrypt 해시. 로그인: ID 없음과 비밀번호 불일치를 같은 401로 처리하고, ID가 없어도 더미 해시와 비교해 응답 시간 차이를 줄임 |
| `account/service/AccountService.java` | 내 정보 조회·수정(전달된 필드만, 빈 문자열은 null), 비밀번호 변경(현재 비밀번호 검증 → 동일 여부 검사 → 해시 저장) |
| `account/controller/*` | Swagger `@Tag`·`@Operation`·`@ApiResponse`, 요청 DTO `@Valid` |

- 인증 API(`/api/auth/**`)는 Authorization 헤더에 만료·위조 토큰이 있어도 무시한다. (Swagger에 이전 토큰이 남아 있어도 로그인 가능)
- 비밀번호·해시·토큰은 응답(가입 응답 등)과 로그에 포함하지 않는다.

### 테스트 결과

`./gradlew test` — **20개 통과, 실패 0**

| 테스트 클래스 | 건수 | 검증 내용 |
|---|---|---|
| `BackendApplicationTests` | 2 | 컨텍스트 로딩(스키마 validate), `/v3/api-docs` 공개 접근 |
| `AuthApiTest` | 7 | 가입 성공(BCrypt 해시 저장, 응답에 비밀번호 없음), 중복 409, 필드 검증 400(loginId·password·roleLevel), 잘못된 JSON 400, 로그인 성공(`expiresIn=7200`), ID 없음·비밀번호 불일치 동일 401, 인증 API의 잘못된 Bearer 헤더 무시 |
| `AccountApiTest` | 11 | 내 정보 조회, 토큰 없음 401, 위조 토큰 401, 만료 토큰 `TOKEN_EXPIRED`, 존재하지 않는 계정 토큰 401, 부분 수정, 변경 필드 없음 400, 공백 이름 400, 비밀번호 변경 후 새 비밀번호 로그인, 현재 비밀번호 불일치 401, 동일 비밀번호 400 |

### 미구현 항목

- 없음 (토큰 블랙리스트·강제 로그아웃·Refresh Token은 명세상 MVP 제외)
