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

---

## 2단계. 프로젝트·참여자 API

**상태: 완료**

| # | API | Method / URL | 상태 |
|---|---|---|---|
| 7.1 | 프로젝트 생성 | `POST /api/projects` | ✅ 구현 |
| 7.2 | 내 프로젝트 목록 | `GET /api/projects` | ✅ 구현 |
| 7.3 | 프로젝트 상세 | `GET /api/projects/{projectId}` | ✅ 구현 |
| 7.4 | 프로젝트 수정 | `PATCH /api/projects/{projectId}` | ✅ 구현 |
| 7.5 | 프로젝트 삭제 (Soft) | `DELETE /api/projects/{projectId}` | ✅ 구현 |
| 8.1 | 참여자 목록 | `GET /api/projects/{projectId}/members` | ✅ 구현 |
| 8.2 | 참여자 추가 | `POST /api/projects/{projectId}/members` | ✅ 구현 |
| 8.3 | 참여자 권한 변경 | `PATCH /api/projects/{projectId}/members/{uid}` | ✅ 구현 |
| 8.4 | 참여자 제외/탈퇴 | `DELETE /api/projects/{projectId}/members/{uid}` | ✅ 구현 |

### 주요 구현 내용

| 파일 | 내용 |
|---|---|
| `project/entity/Project.java` | `project` 테이블 매핑. `detail`은 `columnDefinition = "jsonb"` + `@ColumnTransformer(write = "?::jsonb")`로 String 매핑 (Hibernate JSON FormatMapper에 의존하지 않음) |
| `project/entity/ProjectDetail.java`, `ProjectMember.java` | 명세 3.1의 JSONB 구조. `schemaVersion: 1` |
| `global/jsonb/JsonbObject.java` | JSONB 모델 공통: null 키 미저장(`deletion` 등은 해당 상태일 때만 존재), **정의되지 않은 키 보존**(`@JsonAnySetter/@JsonAnyGetter`) |
| `project/repository/ProjectRepository.java` | `findByIdForUpdate`(FOR UPDATE), `findByIdForShare`(FOR SHARE), 참여 프로젝트 목록 Native Query (`created_by = :uid OR detail->'members' @> [{"uid": ...}]`, `detail->'deletion' IS NULL`) |
| `project/service/ProjectAccessService.java` | 모든 프로젝트 하위 API가 공유하는 접근 검증: 없음·삭제됨 404 → 비참여자 403 → 프로젝트 역할 판정(OWNER는 `created_by`) |
| `project/service/ProjectService.java` | 생성(시공사·시행사만), 목록(키워드 LIKE 이스케이프, 상태 필터), 수정(잠금 후 병합한 기간 검증, `members` 보존), Soft Delete(`detail.deletion`만 추가) |
| `project/service/ProjectMemberService.java` | 참여자 추가·변경·제외. `project` 행 **FOR UPDATE 잠금 후** 중복·권한 검사와 저장을 같은 트랜잭션에서 수행. MANAGER 제한(MANAGER 지정·다른 MANAGER 변경/제외 불가), OWNER 보호, 본인 탈퇴, 깨진 참조는 목록에서 제외하고 WARN 로그 |

### 테스트 결과

`./gradlew test` — **누적 38개 통과, 실패 0** (2단계 신규 18개)

| 테스트 클래스 | 건수 | 검증 내용 |
|---|---|---|
| `ProjectApiTest` | 11 | 생성(OWNER, DB의 `schemaVersion`·`members` 배열·`deletion` 키 없음 확인), 현장직 생성 403, 기간 역전 400, 이름 누락 400, 목록(참여 프로젝트만, 역할·인원수, LIKE 특수문자 `%`·`_` 이스케이프, 상태 필터), 잘못된 페이지·enum 400, 비참여자 403·없는 프로젝트 404, MANAGER 수정 성공·MEMBER 403·수정 후 members 보존, 변경 필드 없음·병합 기간 역전 400, Soft Delete 후 404·목록 제외·DB에 `deletion{deletedAt, deletedBy, reason}` 보존, MANAGER 삭제 403 |
| `ProjectMemberApiTest` | 7 | 추가·목록(OWNER 우선, 전화번호 미노출, uid 저장 형식), 중복·OWNER 추가 409, 없는 계정 404·OWNER 지정 400·MANAGER의 MANAGER 지정 403·MEMBER 추가 403, 권한 변경 규칙(OWNER 409, MANAGER→MANAGER 403, 비참여자 404, 잘못된 UUID 400), 본인 탈퇴·OWNER 탈퇴 409·제외 후 접근 403, **동시 추가 8건 갱신 손실 없음**, **같은 사용자 동시 추가 6건 중 1건만 성공(나머지 409)** |

### 미구현 항목

- 없음 (OWNER 위임은 명세상 MVP 제외)

---

## 3단계. 시공 기록·이미지 API

**상태: 완료**

| # | API | Method / URL | 상태 |
|---|---|---|---|
| 9.1 | 시공 기록 생성 | `POST /api/projects/{projectId}/records` | ✅ 구현 |
| 9.2 | 시공 기록 목록 | `GET /api/projects/{projectId}/records` | ✅ 구현 |
| 9.3 | 시공 기록 상세 | `GET /api/projects/{projectId}/records/{dataId}` | ✅ 구현 (`verifyFiles` 포함) |
| 9.4 | 시공 기록 수정 | `PATCH /api/projects/{projectId}/records/{dataId}` | ✅ 구현 |
| 9.5 | 시공 기록 삭제 (Soft) | `DELETE /api/projects/{projectId}/records/{dataId}` | ✅ 구현 |
| 11.1 | 이미지 업로드 | `POST /api/projects/{projectId}/records/{dataId}/files` | ✅ 구현 |
| 11.2 | 이미지 목록 | `GET /api/projects/{projectId}/records/{dataId}/files` | ✅ 구현 |
| 11.3 | 이미지 다운로드 | `GET /api/files/{storageId}` | ✅ 구현 |
| 11.4 | 이미지 삭제 (Soft) | `DELETE /api/projects/{projectId}/records/{dataId}/files/{storageId}` | ✅ 구현 |

### 주요 구현 내용

| 파일 | 내용 |
|---|---|
| `construction/entity/ConstructionRecord.java` | `data` 테이블 매핑 (Java `record` 키워드와의 혼동을 피하기 위해 패키지명은 `construction`). `metadata` jsonb, `url` text |
| `construction/entity/RecordMetadata.java` 외 | 명세 3.3의 JSONB 구조 전체(`files`, `confirmations`, `aiAnalyses`, `correction`, `deletion`, `location`). 4·5단계에서 그대로 사용 |
| `file/entity/Storage.java` | `storage` 테이블 매핑. `image`는 `byte[]` → BYTEA (`@Lob` 미사용: PostgreSQL에서 OID로 매핑되는 것을 방지). 생성 후 수정 불가(`updatable = false`) |
| `construction/repository/RecordQueryRepository.java` | 목록·통합 검색 공용 동적 Native Query. 날짜(UTC 문자열 비교), 위치 ILIKE(이스케이프), 층 대소문자 무시, 서명 여부, 최근 확인 상태(`confirmations->-1`), 정정·무효화 상태(EXISTS), AI 분석 존재, 반경 검색(bounding box + Haversine). 모든 값은 파라미터 바인딩 |
| `construction/service/RecordAccessService.java` | 기록 조회·FOR UPDATE 잠금, 다른 프로젝트 기록·삭제 기록 404, 수정 권한(OWNER·MANAGER 또는 작성자 MEMBER), 서명 여부(`RECORD_LOCKED`) |
| `construction/service/RecordService.java` | 생성(project FOR SHARE), 목록(KST 날짜 경계 → UTC), 상세, 수정(project FOR SHARE → data FOR UPDATE 후 서명 여부 재확인, location 전체 교체), Soft Delete(서명 기록 409, 정정 참조 409) |
| `construction/service/RecordStatusResolver.java` | 이 기록을 참조하는 정정·무효화 기록을 목록 단위로 한 번에 조회해 `ACTIVE/SUPERSEDED/INVALIDATED` 계산 (원본 행 미변경) |
| `construction/service/RecordSnapshotHasher.java` | 명세 4.4 스냅샷 v1 정규화(키 정렬, null 포함, 위·경도 BigDecimal 문자열, 파일 storageId 정렬) + SHA-256. 서명·검증 공용 |
| `construction/service/RecordIntegrityVerifier.java` | 조회 시 서명 해시 비교(`integrity.status`), `verifyFiles=true`면 첨부·서명 이미지 바이너리 해시 재계산. 불일치 시 dataId만 WARN 로그 |
| `construction/mapper/RecordMapper.java` | Entity+JSONB → `RecordResponse`. 상태·정정 작성자 조회를 목록 단위로 묶어 N+1 방지 |
| `file/service/ImageFileValidator.java` | 명세 11.0 검증 순서: 빈 파일 → 크기(사진 10MB·서명 1MB) → 매직 바이트(JPEG·PNG) → 확장자 일치 → `ImageIO` 헤더 파싱(전체 디코딩 없음) → 해상도·픽셀 제한 → SHA-256. 파일명 경로·제어 문자 제거 |
| `file/service/FileService.java` | 업로드: ①읽기 트랜잭션에서 권한·상태 사전 확인 ②잠금 없이 이미지 검증·요청 내 중복 해시 확인 ③쓰기 트랜잭션에서 project FOR SHARE → data FOR UPDATE 후 재검증(서명·개수·기존 해시 중복), storage INSERT + `metadata.files` 갱신(실패 시 전체 롤백). 다운로드: JSONB 포함 검색으로 소속 기록을 찾아 권한 검증, 연결 없음·다중 연결은 404. 삭제: `files[].deletion`만 추가 |
| `file/controller/FileController.java` | 다운로드 응답 헤더: 저장된 Content-Type, Content-Length, Content-Disposition(UTF-8 파일명), ETag(`"sha256"`), `Cache-Control: private, max-age=3600`, `nosniff`, `If-None-Match` → 304 |

### 테스트 결과

`./gradlew test` — **누적 59개 통과, 실패 0** (3단계 신규 21개)

| 테스트 클래스 | 건수 | 검증 내용 |
|---|---|---|
| `RecordApiTest` | 10 | 생성(UTC 정규화, `schemaVersion`·빈 배열·`deletion`/`correction` 키 없음, `uploaded_by`), VIEWER·비참여자 403, INVALIDATION 유형·미래 일시·위경도 한쪽·제목 누락 400, 목록 필터(KST 날짜 경계, 층 대소문자 무시, 동·공정·키워드 `%` 이스케이프, 유형, 작성자, 정렬·페이지), 잘못된 기간·정렬·날짜 형식·size 400, 다른 프로젝트 기록 404, 작성자·OWNER 수정·타 MEMBER/VIEWER 403·location 전체 교체·`updatedBy`, **서명된 기록 수정·삭제 409**, Soft Delete(행·제목 보존, deletion 기록, 목록 제외), 삭제된 프로젝트의 기록 404, **동시 PATCH 6건에서 필드 갱신 손실 없음** |
| `FileApiTest` | 11 | JPEG·PNG 업로드(바이너리 원본 저장, SHA-256·크기·해상도, 원본 파일명), 텍스트·**MP4(영상)**·확장자 위장 415(storage 미저장), 손상 PNG·해상도 초과 `INVALID_IMAGE`, 10MB 초과 413, 빈 파일 400, 요청 내·기존 중복 이미지 409, VIEWER·타 MEMBER 403, 기록당 20장 초과 400·요청당 11장 400, 목록·다운로드 헤더(Content-Type, ETag, nosniff, private, inline/attachment), If-None-Match 304, 비참여자 403·없는 파일 404·미인증 401·**연결 기록 없는 storage 404**, Soft Delete(목록·다운로드 제외, storage 유지, 같은 이미지 재업로드 가능), 서명된 기록 업로드·삭제 409, `verifyFiles`로 **바이너리 변조 탐지(MISMATCH)** |

> 3단계 테스트의 "서명된 기록"은 4단계 서명 API 구현 전이므로 `confirmations`에 항목을 SQL로 직접 넣어 만들었다. 실제 서명 API 기반 검증은 4단계 테스트에서 수행한다.

### 미구현 항목

- 없음 (영상 업로드, `Range` 요청은 명세상 MVP 제외)

---

## 4단계. 서명·정정·무효화 API

**상태: 완료**

| # | API | Method / URL | 상태 |
|---|---|---|---|
| 12.1 | 확인·서명 등록 | `POST /api/projects/{projectId}/records/{dataId}/confirmations` | ✅ 구현 |
| 12.2 | 확인·서명 이력 조회 | `GET /api/projects/{projectId}/records/{dataId}/confirmations` | ✅ 구현 |
| 10.1 | 정정 기록 생성 | `POST /api/projects/{projectId}/records/{dataId}/corrections` | ✅ 구현 |
| 10.2 | 무효화 기록 생성 | `POST /api/projects/{projectId}/records/{dataId}/invalidation` | ✅ 구현 |
| 10.3 | 정정 이력 조회 | `GET /api/projects/{projectId}/records/{dataId}/history` | ✅ 구현 |

### 주요 구현 내용

| 파일 | 내용 |
|---|---|
| `confirmation/service/ConfirmationService.java` | ①읽기 트랜잭션에서 참여자·계정 역할(시공사·시행사) 사전 확인 ②잠금 없이 요청 값(REJECTED 의견 필수)·서명 이미지(1MB, JPEG·PNG, 4000px) 검증 ③쓰기 트랜잭션에서 project FOR SHARE → data FOR UPDATE 후: 본인 기록 409, 정정됨·무효화됨 409, 사진 없는 PHOTO 409, 50건 초과 400, **같은 확인자의 직전 확인과 같은 status 409**, **첨부 이미지 바이너리 해시 재검증 + 기존 서명 해시와 현재 해시 비교(불일치 시 `INTEGRITY_MISMATCH`)** → 서명 이미지 storage 저장 + 스냅샷 해시와 함께 `confirmations` 추가 |
| `confirmation/controller/ConfirmationController.java` | multipart `request`(JSON) + `signature`(이미지). Swagger에서 `request` 파트를 `application/json`으로 전송하도록 `@Encoding` 지정 |
| `construction/service/CorrectionService.java` | 정정: 원본 data 행 FOR UPDATE → 권한(OWNER·MANAGER·원본 작성자) → INVALIDATION 유형 409 → 서명 전 409 → 무효화됨·이미 정정됨 409(최신 기록 ID 안내) → 새 기록에 `correction{AMENDMENT, originalDataId, rootDataId, reason, correctedBy, correctedAt}` 저장. 무효화: OWNER·MANAGER만, type `INVALIDATION`, 제목 `기록 #id 무효화`, 원본 위치 복사. **원본 행은 잠금만 하고 변경하지 않음**. 이력: rootDataId 기준 체인 조회 후 정정 링크를 따라 현재 기록·최종 상태 계산 |
| `construction/service/RecordService.java` | `persist()` 추가: 일반·정정·무효화 기록 저장 공용 |

#### 서명 이후 수정 차단 지점

| 경로 | 차단 방식 |
|---|---|
| 기록 수정·삭제 (9.4, 9.5) | data FOR UPDATE 후 `confirmations`가 있으면 `RECORD_LOCKED` |
| 이미지 업로드·삭제 (11.1, 11.4) | 동일 |
| 확인 항목·AI 분석 | 추가 전용 (수정·삭제 API 없음), 스냅샷 해시 대상 아님 |
| DB 직접 변경 | 방지 불가 → 조회 시 `integrity.status = MISMATCH`로 탐지, 추가 서명 거부 |

### 테스트 결과

`./gradlew test` — **누적 74개 통과, 실패 0** (4단계 신규 15개)

| 테스트 클래스 | 건수 | 검증 내용 |
|---|---|---|
| `ConfirmationApiTest` | 8 | 서명 성공(확인자 스냅샷, 서명 이미지 저장·다운로드, `snapshotVersion`, 상세 조회 `VERIFIED`·signed=current 해시·`fileStatus VERIFIED`), **서명 후 수정·삭제·이미지 업로드·이미지 삭제 모두 409**, 현장직 403·비참여자 403·본인 기록 409·사진 없는 PHOTO 409·사진 없는 NOTE 허용, REJECTED 의견 누락·NONE·서명 누락 400·비이미지 415·1MB 초과 413(실패 시 storage 미저장), 같은 확인자 동일 status 409·status 변경 허용·다른 확인자 추가, **DB 직접 제목 변경 → `MISMATCH` 탐지 + 추가 서명 409**, **이미지 바이너리 변조 시 서명 거부**, **서명 2건과 수정 4건 동시 실행 후에도 해시 `VERIFIED`** |
| `CorrectionApiTest` | 7 | 서명 전 정정 409, 정정 성공(연결 정보, **원본 metadata 텍스트가 정정 전후 동일**, 원본 `SUPERSEDED`·`supersededByDataId`, status 필터), 타 MEMBER·VIEWER 403·사유 누락 400·이중 정정 409·이전 버전 서명 409, 정정 체인(rootDataId 유지)·이력(어느 기록에서 조회해도 같은 체인, currentDataId), 서명 전 정정 삭제 시 원본 `ACTIVE` 복귀, 무효화(서명 전 409, MEMBER 403, 사유 누락 400, 생성 내용, 원본 불변·`INVALIDATED`, 재무효화·정정 409, 무효화 기록 업로드 409), **같은 원본에 정정 4건 + 무효화 2건 동시 요청 시 1건만 성공** |

> 4단계 1차 실행에서 8건이 실패했다. 원인은 테스트 헬퍼가 JPEG 이미지를 `.png` 파일명으로 업로드한 것으로, 서버가 확장자 불일치를 415로 올바르게 거부한 결과였다. 헬퍼가 내용에 맞는 확장자를 쓰도록 수정한 뒤 전부 통과했다.

### 미구현 항목

- 없음

---

## 5단계. AI 분석·통합 검색 API

**상태: 완료**

| # | API | Method / URL | 상태 |
|---|---|---|---|
| 13.1 | AI 분석 요청·결과 저장 | `POST /api/projects/{projectId}/records/{dataId}/ai-analyses` | ✅ 구현 |
| 13.2 | AI 분석 이력 조회 | `GET /api/projects/{projectId}/records/{dataId}/ai-analyses` | ✅ 구현 |
| 13.3 | 최신 AI 분석 조회 | `GET /api/projects/{projectId}/records/{dataId}/ai-analyses/latest` | ✅ 구현 |
| 14.1 | 시공 이력 통합 검색 | `GET /api/records/search` | ✅ 구현 |

### 주요 구현 내용

| 파일 | 내용 |
|---|---|
| `ai/client/AiAnalysisClient.java` | AI 서버 호출 계약(인터페이스). 테스트에서 Mock으로 교체, 향후 비동기 구현으로 교체 가능 |
| `ai/client/RestAiAnalysisClient.java` | Spring 기본 `RestClient` + JDK HttpClient(연결 5s, 읽기 60s). `POST {base-url}/analyze` multipart(`images[]`, `context` JSON). 응답 검증(model 문자열·result 객체 필수, result 256KB 이하). 오류 분류: 시간 초과 → `AI_SERVER_TIMEOUT`, 연결 실패·4xx/5xx → `AI_SERVER_ERROR`, 형식 오류 → `AI_INVALID_RESPONSE`. 내부 예외 메시지는 저장·응답하지 않고 고정 메시지 사용 |
| `ai/service/AiAnalysisService.java` | ①읽기 트랜잭션: 권한(VIEWER 403), 무효화 기록·무효화된 기록 409, 이력 30건 409, 대상 이미지 결정(미지정 시 최근 5장, 지정 시 중복·소속 검증), 바이너리 로드 ②**트랜잭션·잠금 밖에서 AI 호출** ③쓰기 트랜잭션: project FOR SHARE → data FOR UPDATE 후 `version = max + 1`로 `aiAnalyses` 추가. 실패도 FAILED 이력 저장 후 502·504 응답 |
| `search/service/RecordSearchService.java` | 접근 범위(참여 중·삭제되지 않은 프로젝트) 항상 적용, projectId 지정 시 권한 검증, 기간 366일 제한(한쪽만 지정 시 자동 범위), 반경 파라미터 일괄 검증, `distance,asc`는 반경 검색에서만, 응답에 projectName·thumbnail·fileCount·distanceMeters(Haversine) |
| `construction/repository/RecordQueryRepository.java` | (3단계에서 구현) 9.2 목록과 14.1 검색이 같은 쿼리 빌더를 공유 |

### 테스트 결과

`./gradlew test` — **누적 92개 통과, 실패 0** (5단계 신규 18개)

| 테스트 클래스 | 건수 | 검증 내용 |
|---|---|---|
| `AiAnalysisApiTest` | 6 | (AI 클라이언트 Mock) 분석 성공(Location, version 1, 결과 JSON 저장, 최근 업로드 순 대상, 상세의 `latestAiAnalysis`), 대상 지정·없는 ID·중복·6개 400, **AI 시간 초과 504·잘못된 응답 502 후 FAILED 이력 저장**, latest(successOnly 404 / false 시 FAILED 반환), VIEWER 403·이미지 없음 409(AI 미호출)·30건 초과 409, **서명된 기록에 분석 추가 후에도 integrity `VERIFIED`**, 무효화된 기록 409, **동시 분석 5건의 version이 1~5로 중복 없음** |
| `RestAiAnalysisClientTest` | 6 | (실제 JDK HttpServer) multipart 전송 내용(images 파일명, context JSON)과 응답 파싱, 500 → `AI_SERVER_ERROR`, model 누락·비JSON → `AI_INVALID_RESPONSE`, 지연 응답 → `AI_SERVER_TIMEOUT`, 연결 거부 → `AI_SERVER_ERROR` |
| `RecordSearchApiTest` | 6 | 참여 프로젝트만 검색(타사 기록 제외), projectName·thumbnail·fileCount·files 미포함, projectId 지정 시 비참여 403·없는 프로젝트 404, 기간 검색·from만 지정·366일 초과 400·from>to 400, **반경 500m(약 111m 기록만, distanceMeters≈111.2)·2km 거리순 정렬**, 반경 파라미터 일부·범위 초과·위도 범위 초과·반경 없는 거리 정렬 400, 주소·키워드(공정 포함)·AI 분석 유무·확인 상태(APPROVED/NONE)·층·동 필터, 삭제된 기록·삭제된 프로젝트 제외 |

> 5단계 1차 실행에서 1건이 실패했다. 테스트에서 Mockito 스텁을 `when(...).thenThrow()`로 덮어쓰면서 이전 스텁의 예외가 발생한 테스트 코드 문제였으며, `doThrow().when()`으로 수정해 해결했다.

### 미구현 항목

- AI 비동기 처리(`PENDING` + AI 전용 서비스 토큰 콜백)는 명세상 향후 확장 사항으로 미구현 (`AiAnalysisClient` 인터페이스로 교체 가능하게 구성)

---

## 최종 검증

### 1. 명세 대비 누락 API 검증

`ApiSpecCoverageTest`가 `docs/api.md`의 "API 요약" 표를 파싱해 **명세된 32개 API(Method + URL)** 와 실제 OpenAPI 문서(`/v3/api-docs`)에 등록된 엔드포인트를 비교한다.

- 결과: **32개 모두 구현, 명세에 없는 추가 엔드포인트 0개** (통과)

| 구분 | 명세 | 구현 |
|---|---|---|
| 인증·계정 (5.x, 6.x) | 5 | 5 |
| 프로젝트·참여자 (7.x, 8.x) | 9 | 9 |
| 시공 기록 (9.x) | 5 | 5 |
| 정정·무효화 (10.x) | 3 | 3 |
| 이미지 (11.x) | 4 | 4 |
| 확인·서명 (12.x) | 2 | 2 |
| AI 분석 (13.x) | 3 | 3 |
| 통합 검색 (14.1) | 1 | 1 |
| **합계** | **32** | **32** |

### 2. 전체 빌드·테스트

`./gradlew test --rerun-tasks` — **BUILD SUCCESSFUL, 93개 통과, 실패 0** (약 2분 35초)

| 테스트 클래스 | 건수 |
|---|---|
| BackendApplicationTests | 2 |
| ApiSpecCoverageTest | 1 |
| AuthApiTest / AccountApiTest | 7 / 11 |
| ProjectApiTest / ProjectMemberApiTest | 11 / 7 |
| RecordApiTest / FileApiTest | 10 / 11 |
| ConfirmationApiTest / CorrectionApiTest | 8 / 7 |
| AiAnalysisApiTest / RestAiAnalysisClientTest | 6 / 6 |
| RecordSearchApiTest | 6 |
| **합계** | **93** |

### 3. 실제 DB 스키마 일치 확인 (읽기 전용)

- `.env`의 원격 PostgreSQL(Railway)에 빌드한 jar를 `ddl-auto=validate`로 기동 → **`Started BackendApplication` (스키마 검증 통과)** 확인 후 즉시 종료.
- 데이터는 쓰지 않았다. 원격 DB에서의 CRUD 동작은 테스트 데이터가 공유 DB에 남는 것을 피하기 위해 실행하지 않았고, 대신 같은 `create_table.sql`을 적용한 임베디드 PostgreSQL에서 전 API의 CRUD를 검증했다.

### 4. 명세 준수 점검

| 항목 | 결과 |
|---|---|
| DB 스키마 | `create_table.sql` 미변경. 4개 테이블만 사용, 테이블·컬럼·인덱스 추가 없음 |
| 요청·응답 형식 | 명세의 DTO 구조대로 구현. 오류 응답 `{timestamp, status, code, message, path, errors}` 통일 |
| 권한 | 명세 2.3 권한 매트릭스를 테스트로 검증 (OWNER·MANAGER·MEMBER·VIEWER·비참여자, 계정 역할) |
| 동시성 | 참여자 추가, 기록 수정, 서명+수정, 정정·무효화, AI version 부여의 동시 요청 테스트 통과 |
| 무결성 | 서명 해시 저장·조회 시 검증, DB 직접 변경·이미지 바이너리 변조 탐지 테스트 통과 |
| 민감정보 | 비밀번호·해시·토큰 응답 미포함, 로그에는 dataId·projectId만 기록 |
| Swagger | 전 API `@Tag`·`@Operation`·`@ApiResponse`, DTO `@Schema`, `bearerAuth`, multipart 파일 선택 UI |

### 5. 구현 중 `docs/api.md`에 반영한 변경

| 위치 | 변경 내용 | 사유 |
|---|---|---|
| 4.5 대기 시간 | `jakarta.persistence.lock.timeout` → 커넥션 초기화 SQL `SET lock_timeout = '3s'` | PostgreSQL에서 JPA 양수 잠금 타임아웃 힌트가 적용되지 않음 |
| 10.1, 10.2 예외 | `409 INVALID_RECORD_TYPE`(대상이 무효화 기록) 추가 | 무효화 기록은 정정·무효화 대상이 아니라는 기존 규칙의 오류 코드 명시 |
| 11.1, 12.1 로직 | 0단계 "읽기 트랜잭션에서 권한 사전 확인" 추가 | 권한 없는 요청에 대한 이미지 처리 방지 |
| 15 오류 코드 | `ACCESS_DENIED`(403), `RESOURCE_NOT_FOUND`(404) 추가 | 보안 필터 단계·존재하지 않는 URL의 공통 오류 응답 |
| 문서 상태 | "구현 완료"로 변경 | |

### 6. 실행 방법 및 남은 확인 사항

- 실행 시 환경변수 필요: `DB_URL`, `DB_USER`, `DB_PASSWORD`, **`JWT_SECRET`(32바이트 이상, 신규)**, `AI_SERVER_URL`(선택, 기본 `http://localhost:8000`)
- Swagger UI: `http://localhost:8080/swagger-ui.html` → 로그인 API로 토큰 발급 → `Authorize`에 입력
- **AI 서버 실제 요청·응답 형식은 미확정**이다. 현재는 명세 13.0의 가정 형식으로 구현되어 있으며, 형식이 확정되면 `RestAiAnalysisClient`만 수정하면 된다.
