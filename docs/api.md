# API Specification

건설 현장 시공 이력 관리 시스템 (Buildger) REST API 명세서

- 기준 스키마: `src/main/resources/sql/create_table.sql` (**변경하지 않음**)
- 참고 문서: `docs/database.md`
- 문서 상태: **MVP 확정안** — 아직 구현되지 않음

> **MVP 원칙:** 기존 `account`, `project`, `data`, `storage` 4개 테이블의 테이블·컬럼·제약조건·인덱스를 변경하지 않는다. 참여자, 파일 연결, 서명, AI 분석, 정정·무효화, Soft Delete 정보는 모두 기존 JSONB 컬럼(`project.detail`, `data.metadata`)에 [3장](#3-jsonb-저장-구조)의 구조로 저장한다.

---

## 0. 목차

1. [공통 규칙](#1-공통-규칙)
2. [권한 모델](#2-권한-모델)
3. [JSONB 저장 구조](#3-jsonb-저장-구조)
4. [데이터 무결성 및 동시성 규칙](#4-데이터-무결성-및-동시성-규칙)
5. [인증 API](#5-인증-api)
6. [계정 API](#6-계정-api)
7. [프로젝트 API](#7-프로젝트-api)
8. [프로젝트 참여자 API](#8-프로젝트-참여자-api)
9. [시공 기록 API](#9-시공-기록-api)
10. [정정·무효화 API](#10-정정무효화-api)
11. [이미지 파일 API](#11-이미지-파일-api)
12. [담당자 확인·서명 API](#12-담당자-확인서명-api)
13. [AI 분석 API](#13-ai-분석-api)
14. [시공 이력 검색 API](#14-시공-이력-검색-api)
15. [오류 코드 목록](#15-오류-코드-목록)
16. [MVP 결정 사항](#16-mvp-결정-사항)
17. [현재 스키마의 제약 사항](#17-현재-스키마의-제약-사항)
18. [향후 개선 사항 (MVP 미적용)](#18-향후-개선-사항-mvp-미적용)

### API 요약

| # | 기능 | Method | URL | 인증 |
|---|---|---|---|---|
| 5.1 | 회원가입 | POST | `/api/auth/signup` | X |
| 5.2 | 로그인 | POST | `/api/auth/login` | X |
| 6.1 | 내 계정 조회 | GET | `/api/accounts/me` | O |
| 6.2 | 내 계정 수정 | PATCH | `/api/accounts/me` | O |
| 6.3 | 비밀번호 변경 | PATCH | `/api/accounts/me/password` | O |
| 7.1 | 프로젝트 생성 | POST | `/api/projects` | O |
| 7.2 | 내 프로젝트 목록 | GET | `/api/projects` | O |
| 7.3 | 프로젝트 상세 | GET | `/api/projects/{projectId}` | O |
| 7.4 | 프로젝트 수정 | PATCH | `/api/projects/{projectId}` | O |
| 7.5 | 프로젝트 삭제 (Soft) | DELETE | `/api/projects/{projectId}` | O |
| 8.1 | 참여자 목록 | GET | `/api/projects/{projectId}/members` | O |
| 8.2 | 참여자 추가 | POST | `/api/projects/{projectId}/members` | O |
| 8.3 | 참여자 권한 변경 | PATCH | `/api/projects/{projectId}/members/{uid}` | O |
| 8.4 | 참여자 제외/탈퇴 | DELETE | `/api/projects/{projectId}/members/{uid}` | O |
| 9.1 | 시공 기록 생성 | POST | `/api/projects/{projectId}/records` | O |
| 9.2 | 시공 기록 목록 | GET | `/api/projects/{projectId}/records` | O |
| 9.3 | 시공 기록 상세 | GET | `/api/projects/{projectId}/records/{dataId}` | O |
| 9.4 | 시공 기록 수정 | PATCH | `/api/projects/{projectId}/records/{dataId}` | O |
| 9.5 | 시공 기록 삭제 (Soft) | DELETE | `/api/projects/{projectId}/records/{dataId}` | O |
| 10.1 | 정정 기록 생성 | POST | `/api/projects/{projectId}/records/{dataId}/corrections` | O |
| 10.2 | 무효화 기록 생성 | POST | `/api/projects/{projectId}/records/{dataId}/invalidation` | O |
| 10.3 | 정정 이력 조회 | GET | `/api/projects/{projectId}/records/{dataId}/history` | O |
| 11.1 | 파일 업로드 | POST | `/api/projects/{projectId}/records/{dataId}/files` | O |
| 11.2 | 파일 목록 | GET | `/api/projects/{projectId}/records/{dataId}/files` | O |
| 11.3 | 파일 다운로드 | GET | `/api/files/{storageId}` | O |
| 11.4 | 파일 삭제 (Soft) | DELETE | `/api/projects/{projectId}/records/{dataId}/files/{storageId}` | O |
| 12.1 | 확인·서명 등록 | POST | `/api/projects/{projectId}/records/{dataId}/confirmations` | O |
| 12.2 | 확인·서명 이력 조회 | GET | `/api/projects/{projectId}/records/{dataId}/confirmations` | O |
| 13.1 | AI 분석 요청·결과 저장 | POST | `/api/projects/{projectId}/records/{dataId}/ai-analyses` | O |
| 13.2 | AI 분석 이력 조회 | GET | `/api/projects/{projectId}/records/{dataId}/ai-analyses` | O |
| 13.3 | 최신 AI 분석 조회 | GET | `/api/projects/{projectId}/records/{dataId}/ai-analyses/latest` | O |
| 14.1 | 시공 이력 통합 검색 | GET | `/api/records/search` | O |

> 프로젝트 내부의 날짜·위치 필터링은 9.2 목록 API로 제공하고, 참여 중인 모든 프로젝트를 대상으로 하는 검색은 14.1에서 제공한다.

---

## 1. 공통 규칙

### 1.1 기본 사항

| 항목 | 규칙 |
|---|---|
| Base URL | `/api` |
| Content-Type | `application/json; charset=UTF-8` (파일 업로드는 `multipart/form-data`) |
| 이미지 다운로드 | 실제 이미지 유형의 Content-Type (`image/jpeg`, `image/png`) 바이너리 |
| 날짜·시간 입력 | ISO-8601 오프셋 포함 (`2026-11-17T09:30:00+09:00`) |
| 날짜·시간 저장·응답 | UTC 고정 형식 `yyyy-MM-dd'T'HH:mm:ss'Z'` (예: `2026-11-17T00:30:00Z`) |
| 날짜 | `yyyy-MM-dd`, 검색 기준 시간대는 `Asia/Seoul` |
| ID 타입 | 계정 `uid`: UUID 문자열 / `projectId`, `dataId`, `storageId`: 정수(Long) / 확인·분석 ID: UUID 문자열 |
| Swagger UI | `/swagger-ui/index.html` (Springdoc OpenAPI) |

### 1.2 인증 방식

- 로그인 성공 시 **JWT Access Token**(Stateless)을 발급한다.
- 인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더를 사용한다.
- Swagger UI에 `bearerAuth` (HTTP, scheme `bearer`, bearerFormat `JWT`) 보안 스키마를 등록해 `Authorize` 버튼으로 토큰을 입력할 수 있게 한다.
- 토큰 Payload: `sub`(uid), `role`(role_level), `iat`, `exp`. 비밀번호·개인정보는 포함하지 않는다.
- 만료 시간: **2시간** (`jwt.access-token-expiration=7200s`). 해커톤 시연 중 토큰 만료로 인한 중단을 방지하기 위한 값이다.
- 서명 키는 환경변수 `JWT_SECRET`(HS256, 32바이트 이상)으로 주입하고 로그에 출력하지 않는다.
- 토큰 저장 테이블이 없으므로 Refresh Token, 토큰 블랙리스트, 강제 로그아웃은 MVP 범위에서 제외한다. 로그아웃은 클라이언트의 토큰 폐기로 처리한다.
- 요청마다 토큰의 `sub`(uid)로 계정을 조회하며, 계정이 없으면 401 `UNAUTHORIZED`로 처리한다.

### 1.3 공통 오류 응답 형식

모든 오류는 `@RestControllerAdvice`에서 아래 형식으로 통일한다.

```json
{
  "timestamp": "2026-11-17T00:30:00Z",
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "path": "/api/auth/signup",
  "errors": [
    { "field": "loginId", "reason": "4~100자의 영문, 숫자, 일부 특수문자(._-)만 사용할 수 있습니다." }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| timestamp | string(datetime) | 오류 발생 시각 |
| status | integer | HTTP 상태 코드 |
| code | string | 애플리케이션 오류 코드 ([15장](#15-오류-코드-목록)) |
| message | string | 사용자용 메시지 (SQL, 스택트레이스, 토큰 등 내부 정보 미포함) |
| path | string | 요청 URI |
| errors | array | 필드 검증 오류 목록. 검증 오류가 아니면 빈 배열 |

### 1.4 공통 HTTP 상태 코드

| 상태 | 의미 |
|---|---|
| 200 OK | 조회·수정 성공 |
| 201 Created | 생성 성공 (`Location` 헤더 포함) |
| 204 No Content | 삭제 성공 |
| 400 Bad Request | 검증 실패, 잘못된 파라미터 |
| 401 Unauthorized | 토큰 없음·만료·위조, 로그인 실패 |
| 403 Forbidden | 권한 없음 |
| 404 Not Found | 리소스 없음 (Soft Delete된 리소스 포함) |
| 409 Conflict | 중복, 상태 충돌 (서명된 기록 변경 등) |
| 413 Payload Too Large | 파일·요청 크기 초과 |
| 415 Unsupported Media Type | 허용되지 않은 파일 형식 |
| 502 Bad Gateway | AI 서버 오류 |
| 504 Gateway Timeout | AI 서버 응답 시간 초과 |
| 500 Internal Server Error | 서버 오류 |

> 인증이 필요한 모든 API는 401(`UNAUTHORIZED`, `TOKEN_EXPIRED`)이 공통으로 발생할 수 있으므로 각 API의 예외 표에서는 생략한다.

### 1.5 페이지네이션

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| page | integer | X | 0 | 0부터 시작하는 페이지 번호 (≥ 0) |
| size | integer | X | 20 | 페이지 크기 (1~100) |

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### 1.6 Soft Delete 조회 규칙

- 삭제 처리된 프로젝트, 기록, 파일은 모든 일반 조회·검색·다운로드에서 제외하며, 직접 조회 시 `404`를 반환한다.
- 삭제된 프로젝트에 속한 기록·파일도 모두 조회에서 제외한다.
- 원본 데이터(행, JSONB, `storage` 바이너리)는 삭제하지 않는다.
- MVP에는 삭제 데이터 조회·복구 API가 없다. (관리자 기능 미구현)

---

## 2. 권한 모델

권한은 **계정 역할(`account.role_level`)** 과 **프로젝트 내 역할(`projectRole`)** 두 단계로 검증한다. 회원가입만으로는 어떤 프로젝트 데이터에도 접근할 수 없으며, `project.created_by`(OWNER)이거나 `project.detail.members`에 등록된 사용자만 해당 프로젝트에 접근할 수 있다.

### 2.1 계정 역할

| role_level | roleName | 역할 |
|---|---|---|
| 1 | `FIELD_WORKER` | 현장직 |
| 2 | `CONTRACTOR` | 시공사 |
| 3 | `DEVELOPER` | 시행사 |

### 2.2 프로젝트 역할

| 역할 | 저장 위치 | 설명 |
|---|---|---|
| `OWNER` | `project.created_by` | 프로젝트 생성자. 프로젝트당 1명, members 배열에는 넣지 않음 |
| `MANAGER` | `detail.members[].projectRole` | 프로젝트 관리자 |
| `MEMBER` | `detail.members[].projectRole` | 일반 참여자 (기록 작성) |
| `VIEWER` | `detail.members[].projectRole` | 열람 전용 |

### 2.3 권한 매트릭스

| 작업 | OWNER | MANAGER | MEMBER | VIEWER | 비참여자 |
|---|---|---|---|---|---|
| 프로젝트·기록·파일·확인·AI 결과·이력 조회 | O | O | O | O | X (403) |
| 프로젝트 수정 | O | O | X | X | X |
| 프로젝트 삭제 | O | X | X | X | X |
| 참여자 추가·권한 변경·제외 | O | O (※1) | X | X | X |
| 시공 기록 생성, 파일 업로드 | O | O | O (※2) | X | X |
| 서명 전 기록 수정·삭제, 파일 삭제 | O | O | 본인 작성 기록만 | X | X |
| 정정 기록 생성 | O | O | 원본 작성자만 | X | X |
| 무효화 기록 생성 | O | O | X | X | X |
| AI 분석 요청 | O | O | O | X | X |
| 담당자 확인·서명 | role_level 2·3 이면서 프로젝트 참여자 (※3) | | | | X |

- ※1 MANAGER는 OWNER·다른 MANAGER를 변경·제외할 수 없고, MANAGER를 지정할 수 없다(OWNER만 가능).
- ※2 파일 업로드는 본인이 작성한 기록에만 가능하다. (OWNER·MANAGER는 모든 기록)
- ※3 확인·서명은 시공사(2)·시행사(3) 계정이 프로젝트 역할(VIEWER 포함)과 무관하게 참여자이면 가능하다. 단, 본인이 작성한 기록은 확인할 수 없다.
- 프로젝트 생성은 시공사(2)·시행사(3) 계정만 가능하다.

---

## 3. JSONB 저장 구조

JSONB 내부 필드는 DB 제약조건으로 강제되지 않으므로, 아래 구조를 Java 타입(record/class)으로 정의하고 **모든 쓰기를 Service 계층의 검증을 거쳐 수행**한다.

### 3.0 공통 규칙

| 규칙 | 내용 |
|---|---|
| 스키마 버전 | 최상위에 `"schemaVersion": 1`을 저장한다. 구조가 바뀌면 버전을 올리고 읽기 시 하위 호환 처리한다. |
| 시간 형식 | 모든 시각은 UTC `yyyy-MM-dd'T'HH:mm:ss'Z'` 고정 형식 문자열 (문자열 비교로 범위 검색 가능) |
| 상태 키 | `deletion`, `correction`은 **해당 상태일 때만 키를 추가**하고, 그 외에는 키 자체를 두지 않는다. JSON `null`을 저장하지 않는다. (SQL `metadata->'deletion' IS NULL` 조건과 일치시키기 위함) |
| 서버 관리 필드 | `createdAt`, `updatedAt`, `updatedBy`, `members`, `files`, `confirmations`, `aiAnalyses`, `correction`, `deletion`은 일반 수정 요청으로 덮어쓸 수 없다. |
| 알 수 없는 키 | 읽을 때 무시하지 않고 보존한다. (`@JsonAnySetter`/`@JsonAnyGetter`) 기존 데이터 손실 방지 |
| 누락 필드 | API를 거치지 않고 생성된 기존 행은 필드가 없을 수 있으므로, 읽기 시 배열은 빈 배열로, 나머지는 null로 처리한다. |

### 3.1 `project.detail`

```json
{
  "schemaVersion": 1,
  "description": "지하 3층, 지상 15층 업무시설",
  "address": "서울특별시 강남구 테헤란로 123",
  "startDate": "2026-03-01",
  "endDate": "2027-08-31",
  "status": "IN_PROGRESS",
  "createdAt": "2026-11-17T00:30:00Z",
  "updatedAt": "2026-11-17T00:30:00Z",
  "updatedBy": "1f0e3c2a-6b7d-4c1e-9a2b-3c4d5e6f7a8b",
  "members": [
    {
      "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
      "projectRole": "MEMBER",
      "addedBy": "1f0e3c2a-6b7d-4c1e-9a2b-3c4d5e6f7a8b",
      "addedAt": "2026-11-17T00:35:00Z",
      "updatedAt": "2026-11-17T00:35:00Z"
    }
  ],
  "deletion": {
    "deletedAt": "2026-12-01T00:00:00Z",
    "deletedBy": "1f0e3c2a-6b7d-4c1e-9a2b-3c4d5e6f7a8b",
    "reason": "프로젝트 종료"
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| schemaVersion | integer | O | 1 |
| description | string | X | 설명 (최대 2000자) |
| address | string | X | 현장 주소 (최대 255자) |
| startDate / endDate | string(date) | X | 착공일 / 준공 예정일 |
| status | string | O | `PLANNED` \| `IN_PROGRESS` \| `COMPLETED` \| `SUSPENDED` |
| createdAt / updatedAt | string(datetime) | O | 생성 / 수정 시각 |
| updatedBy | string(uuid) | X | 마지막 수정자 |
| members | array | O | 참여자 목록 (OWNER 제외). `uid`는 배열 내 유일 |
| members[].projectRole | string | O | `MANAGER` \| `MEMBER` \| `VIEWER` |
| deletion | object | X | Soft Delete 정보 (삭제 시에만 존재) |

### 3.2 `data` 행 사용 규칙

시공 기록 1건 = `data` 1행. 정정·무효화 기록도 새 `data` 행으로 생성한다.

| 컬럼 | 사용 방식 |
|---|---|
| project_id | 소속 프로젝트 |
| uploaded_by | 기록 작성자 uid |
| type | `PHOTO` \| `INSPECTION` \| `NOTE` \| `INVALIDATION` (`INVALIDATION`은 무효화 API로만 생성) |
| metadata | 아래 구조 |
| url | 외부 참조 자료 URL (선택) |

### 3.3 `data.metadata`

```json
{
  "schemaVersion": 1,

  "title": "3층 슬래브 철근 배근",
  "description": "D13 @200 배근 완료",
  "process": "철근콘크리트공사",
  "capturedAt": "2026-11-16T05:20:00Z",
  "location": {
    "site": "A동",
    "building": "본관",
    "floor": "3F",
    "zone": "X1-X3 / Y1-Y4",
    "address": "서울특별시 강남구 테헤란로 123",
    "latitude": 37.501274,
    "longitude": 127.039585
  },

  "createdAt": "2026-11-17T00:40:00Z",
  "updatedAt": "2026-11-17T00:40:00Z",
  "updatedBy": "8a6e0804-2bd0-4672-b79d-d97027f9071a",

  "files": [
    {
      "storageId": 15,
      "originalName": "slab_rebar_01.jpg",
      "contentType": "image/jpeg",
      "size": 2483021,
      "sha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
      "width": 4032,
      "height": 3024,
      "uploadedBy": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
      "uploadedAt": "2026-11-17T00:41:00Z"
    }
  ],

  "confirmations": [
    {
      "confirmationId": "c2f1a9b0-5d3e-4f7a-8b1c-2d3e4f5a6b7c",
      "confirmerUid": "1f0e3c2a-6b7d-4c1e-9a2b-3c4d5e6f7a8b",
      "confirmerName": "김감독",
      "confirmerOrganization": "OO개발",
      "confirmerRoleLevel": 3,
      "status": "APPROVED",
      "comment": "배근 간격 확인 완료",
      "signature": {
        "storageId": 16,
        "contentType": "image/png",
        "size": 18233,
        "sha256": "3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b"
      },
      "snapshotVersion": 1,
      "recordSnapshotSha256": "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
      "confirmedAt": "2026-11-17T01:00:00Z"
    }
  ],

  "aiAnalyses": [
    {
      "analysisId": "e1d2c3b4-a5f6-4789-90ab-cdef01234567",
      "version": 1,
      "status": "SUCCESS",
      "model": "rebar-detector-v2",
      "targetStorageIds": [15],
      "summary": "철근 간격 평균 198mm, 기준 충족",
      "result": { "rebarSpacingMm": 198, "defects": [] },
      "errorCode": null,
      "errorMessage": null,
      "requestedBy": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
      "requestedAt": "2026-11-17T00:44:50Z",
      "completedAt": "2026-11-17T00:45:10Z"
    }
  ],

  "correction": {
    "kind": "AMENDMENT",
    "originalDataId": 101,
    "rootDataId": 101,
    "reason": "층 정보 오기재 (3F → 4F)",
    "correctedBy": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
    "correctedAt": "2026-11-18T02:00:00Z"
  },

  "deletion": {
    "deletedAt": "2026-11-18T03:00:00Z",
    "deletedBy": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
    "reason": "중복 등록"
  }
}
```

> 위 예시는 모든 키를 보여주기 위한 것이다. 실제로 `correction`은 정정·무효화 기록에만, `deletion`은 삭제된 기록에만 존재하며, 서명된 기록에는 `deletion`이 생길 수 없다.

#### 필드 분류

| 분류 | 필드 | 변경 규칙 |
|---|---|---|
| **본문 필드** (서명 보호 대상) | `type`(컬럼), `url`(컬럼), `title`, `description`, `process`, `capturedAt`, `location`, 삭제되지 않은 `files`, `correction` | 서명 전: 수정 가능 / **서명 후: 변경 불가** |
| **추가 전용 이력** | `confirmations`, `aiAnalyses` | 항목 추가만 가능, 수정·삭제 불가. 서명 후에도 추가 가능 |
| **시스템 필드** | `schemaVersion`, `createdAt`, `updatedAt`, `updatedBy` | 서버만 기록 |
| **상태 필드** | `deletion`, `files[].deletion` | 서명 전 기록에만 추가 가능 |

#### 하위 객체 정의

| 객체 | 필드 | 타입 | 설명 |
|---|---|---|---|
| location | site, building, zone | string | 최대 100자 |
| | floor | string | 최대 20자 (예: B2, 3F) |
| | address | string | 최대 255자 |
| | latitude / longitude | number | -90~90 / -180~180, 함께 입력 |
| files[] | storageId | integer | `storage.storage_id` (전체 기록 중 1곳에만 연결) |
| | originalName | string | 원본 파일명 (`storage.name`과 동일) |
| | contentType | string | 매직 바이트로 판별한 MIME 타입 |
| | size | integer | 바이트 |
| | sha256 | string | 64자리 소문자 hex |
| | width / height | integer | 이미지 픽셀 크기 (업로드 시 헤더에서 읽음) |
| | uploadedBy / uploadedAt | string | 업로드 사용자 / 시각 |
| | deletion | object | 파일 Soft Delete 정보 (삭제 시에만) |
| confirmations[] | status | string | `APPROVED` \| `REJECTED` |
| | confirmer* | | 확인 당시 계정 정보 스냅샷 |
| | signature | object | 서명 이미지 (`storage` 저장) |
| | snapshotVersion | integer | 해시 정규화 규칙 버전 (현재 1) |
| | recordSnapshotSha256 | string | 서명 시점 기록 데이터 해시 ([4.4](#44-서명-스냅샷-해시)) |
| aiAnalyses[] | version | integer | 기록 내 1부터 증가 |
| | status | string | `SUCCESS` \| `FAILED` (`PENDING`은 향후 비동기용 예약값) |
| | errorCode | string | `AI_SERVER_ERROR` \| `AI_SERVER_TIMEOUT` \| `AI_INVALID_RESPONSE` |
| correction | kind | string | `AMENDMENT`(정정) \| `INVALIDATION`(무효화) |
| | originalDataId | integer | 정정·무효화 대상 기록 |
| | rootDataId | integer | 정정 체인의 최초 원본 기록 |
| | reason | string | 사유 (1~1000자) |
| | correctedBy / correctedAt | string | 작성자 / 시각 |
| deletion | deletedAt, deletedBy, reason | string | 삭제 시각, 삭제자 uid, 사유(선택, 최대 500자) |

### 3.4 `storage` 사용 규칙

| 컬럼 | 사용 방식 |
|---|---|
| storage_id | 파일 ID |
| name | 원본 파일명 (경로 문자 제거, 최대 255자) |
| image | 현장 사진·서명 이미지 바이너리 (BYTEA). **이미지 외 파일은 저장하지 않는다.** |

- `storage`에는 소속·형식·크기·해시 컬럼이 없으므로, 이 정보는 `data.metadata.files[]` 또는 `confirmations[].signature`에 저장한다.
- `storage` 행은 생성 후 수정하지 않으며, Soft Delete된 파일도 삭제하지 않는다.

### 3.5 기록 상태 (계산값)

기록의 상태는 별도로 저장하지 않고 다른 기록의 `correction`을 조회해 계산한다. 원본 기록 행을 변경하지 않기 위함이다.

| 상태 | 조건 |
|---|---|
| `ACTIVE` | 아래에 해당하지 않음 |
| `SUPERSEDED` | 삭제되지 않은 `AMENDMENT` 기록이 이 기록을 `originalDataId`로 참조 |
| `INVALIDATED` | 삭제되지 않은 `INVALIDATION` 기록이 이 기록을 `originalDataId`로 참조 |

서명 여부(`locked`)는 `confirmations`가 1건 이상이면 `true`다.

---

## 4. 데이터 무결성 및 동시성 규칙

JSONB 내부 데이터에는 FK·UNIQUE·CHECK 제약을 걸 수 없으므로, 아래 규칙을 **Service 계층에서 강제**한다. 이 규칙은 API 명세와 Spring Boot 구현에 동일하게 적용한다.

### 4.1 기본 원칙

| 원칙 | 내용 |
|---|---|
| 단일 쓰기 경로 | `project.detail`, `data.metadata`, `storage`는 Service 계층을 통해서만 변경한다. Controller·Mapper는 JSONB를 직접 수정하지 않는다. |
| 검사와 변경의 원자성 | 참조 검증, 중복 검사, 상태 검사, JSONB 변경을 **하나의 `@Transactional` 메서드 안에서, 행 잠금을 획득한 뒤** 수행한다. 잠금 전에 읽은 값으로 판단하지 않는다. |
| 전체 교체 저장 | JSONB는 잠금 후 읽은 값을 Java 객체로 변환 → 수정 → 컬럼 전체를 다시 저장(read-modify-write)한다. 잠금 없이 읽은 오래된 값으로 `jsonb_set` 부분 갱신을 하지 않는다. |
| 실패 시 전체 롤백 | 검증 실패·예외 발생 시 같은 트랜잭션의 `storage` INSERT를 포함한 모든 변경을 롤백한다. |

### 4.2 참조 유효성 검증 (FK 대체)

| JSONB 참조 | 가리키는 대상 | 검증 내용 | 검증 시점 |
|---|---|---|---|
| `detail.members[].uid` | `account.uid` | 계정 존재 여부 (loginId로 조회해 uid 획득) | 참여자 추가, `project` 행 잠금 후 |
| `detail.members[].addedBy`, `deletion.deletedBy` 등 작성자 필드 | `account.uid` | 인증된 요청자 uid만 기록 (클라이언트 입력 불허) | 쓰기 시 |
| `metadata.files[].storageId` | `storage.storage_id` | 같은 트랜잭션에서 INSERT한 행의 ID만 기록 | 파일 업로드 |
| `metadata.confirmations[].signature.storageId` | `storage.storage_id` | 같은 트랜잭션에서 INSERT한 행의 ID만 기록 | 확인·서명 |
| `metadata.correction.originalDataId` | `data.data_id` | 존재, **같은 프로젝트**, 삭제되지 않음, 서명됨, 정정·무효화되지 않음 | 정정·무효화 생성, 원본 행 잠금 후 |
| `metadata.correction.rootDataId` | `data.data_id` | 클라이언트 입력 불허, 원본의 `rootDataId` 또는 원본 `dataId`로 서버가 계산 | 정정·무효화 생성 |
| `aiAnalyses[].targetStorageIds` | `metadata.files[].storageId` | 해당 기록의 삭제되지 않은 이미지 파일인지 | AI 분석 요청 |
| 요청 경로의 `storageId` (파일 삭제) | `metadata.files[].storageId` | 해당 기록에 연결된 파일인지 | 파일 삭제, `data` 행 잠금 후 |

- 클라이언트가 임의의 `storageId`를 기록에 **연결**하는 API는 없다. 파일 연결은 업로드 API에서 서버가 생성한 `storageId`로만 이루어지므로, 하나의 `storage` 행은 하나의 기록에만 연결된다.
- 조회 시 참조가 깨져 있으면(예: 계정이 없는 member uid, 같은 `storageId`가 두 기록에 연결됨) 해당 항목을 응답에서 제외하고 `dataId`·`projectId`만 포함한 WARN 로그를 남긴다.

### 4.3 중복 방지 (UNIQUE 대체)

| 대상 | 규칙 | 오류 | 직렬화 기준 잠금 |
|---|---|---|---|
| 프로젝트 참여자 | `members[].uid`는 배열 내 유일, OWNER(`created_by`)와도 중복 불가 | 409 `ALREADY_PROJECT_MEMBER` | `project` 행 |
| 기록 첨부파일 | 같은 기록의 삭제되지 않은 파일 중 **동일 SHA-256** 연결 불가. 한 요청 안의 동일 파일도 거부 | 409 `DUPLICATE_FILE` | `data` 행 |
| 정정·무효화 | 원본 1개당 삭제되지 않은 정정 또는 무효화 기록은 최대 1개 | 409 `RECORD_ALREADY_CORRECTED`, `RECORD_INVALIDATED` | 원본 `data` 행 |
| 확인·서명 | 같은 확인자가 직전 본인 확인과 **같은 status**로 다시 등록 불가 (중복 클릭 방지) | 409 `DUPLICATE_CONFIRMATION` | `data` 행 |
| AI 분석 version | `version`은 기록 내 유일, `max + 1`로 부여 | — (잠금으로 보장) | `data` 행 |
| AI 분석 대상 | `targetStorageIds` 내 중복 ID 불가 | 400 `INVALID_INPUT` | — |
| 로그인 ID | DB `UNIQUE` 제약 사용 (사전 조회 + 제약 위반 예외 변환) | 409 `DUPLICATE_LOGIN_ID` | DB 제약 |

> **불변 조건:** 같은 규칙을 검사하는 모든 쓰기는 반드시 위 표의 같은 행 잠금을 먼저 획득한다. 예를 들어 정정 기록 생성과 무효화 기록 생성은 둘 다 원본 `data` 행을 잠그므로, 동시에 요청해도 둘 중 하나만 성공한다. 이 조건이 지켜지므로 PostgreSQL 기본 격리 수준(READ COMMITTED)으로 충분하다.

### 4.4 서명 스냅샷 해시

서명 시점의 기록 데이터를 기준으로 SHA-256 해시를 만들어 저장하고, 조회할 때마다 현재 데이터와 비교한다.

#### 해시 대상 (스냅샷 v1)

```json
{
  "snapshotVersion": 1,
  "dataId": 101,
  "projectId": 1,
  "type": "PHOTO",
  "url": null,
  "uploadedBy": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
  "createdAt": "2026-11-17T00:40:00Z",
  "title": "3층 슬래브 철근 배근",
  "description": "D13 @200 배근 완료",
  "process": "철근콘크리트공사",
  "capturedAt": "2026-11-16T05:20:00Z",
  "location": { "address": null, "building": "본관", "floor": "3F", "latitude": "37.501274", "longitude": "127.039585", "site": "A동", "zone": "X1-X3 / Y1-Y4" },
  "files": [ { "sha256": "9f86d0...0a08", "storageId": 15 } ],
  "correction": null
}
```

- `dataId`·`projectId`를 포함해 다른 기록의 해시를 옮겨 붙일 수 없게 한다.
- `files`는 삭제되지 않은 파일만, `storageId` 오름차순으로 포함한다.
- `confirmations`, `aiAnalyses`, `updatedAt`, `updatedBy`는 포함하지 않는다. (서명 후에도 추가되는 이력이므로)

#### 정규화 규칙 (Canonical JSON)

| 규칙 | 내용 |
|---|---|
| 키 순서 | 모든 객체의 키를 사전순 정렬 |
| 공백 | 공백·줄바꿈 없음 |
| 누락 값 | 위 키는 항상 포함하고, 값이 없으면 `null` |
| 숫자 | 위·경도는 `BigDecimal.stripTrailingZeros().toPlainString()` 문자열 |
| 시각 | 저장 형식 그대로 (UTC `yyyy-MM-dd'T'HH:mm:ss'Z'`) |
| 인코딩 | UTF-8 바이트에 SHA-256, 소문자 hex 64자 |

정규화 로직은 `RecordSnapshotHasher` 한 곳에서만 구현하고, 서명과 검증에 같은 코드를 사용한다. 규칙을 바꿔야 하면 `snapshotVersion`을 올리고, 각 확인 항목에 저장된 버전으로 검증한다.

#### 서명 시

1. `data` 행을 잠근 뒤, 삭제되지 않은 각 첨부파일의 `storage.image` SHA-256을 다시 계산해 `files[].sha256`과 비교한다. 불일치하면 409 `INTEGRITY_MISMATCH`로 서명을 거부한다. (변조된 파일 위에 서명하는 것을 방지)
2. 이미 서명된 기록이면 현재 스냅샷 해시가 기존 서명 해시와 같은지 확인한다. 다르면 409 `INTEGRITY_MISMATCH`.
3. 스냅샷 해시를 계산해 `confirmations[].recordSnapshotSha256`, `snapshotVersion`과 함께 저장한다.

#### 조회 시 검증 (`RecordResponse.integrity`)

| 필드 | 타입 | 설명 |
|---|---|---|
| status | string | `NOT_SIGNED`(서명 없음) \| `VERIFIED`(모든 서명 해시와 현재 해시 일치) \| `MISMATCH`(하나라도 불일치) |
| signedSnapshotSha256 | string \| null | 최초 서명의 해시 |
| currentSnapshotSha256 | string \| null | 현재 데이터로 다시 계산한 해시 (서명 없으면 null) |
| fileStatus | string | `NOT_CHECKED` \| `VERIFIED` \| `MISMATCH` — 파일 바이너리 해시 검증 결과 |
| verifiedAt | string(datetime) | 검증 시각 |

- 기록 상세(9.3)와 목록·검색 응답은 항상 `status`를 계산한다. (JSONB만 사용하므로 추가 DB 조회 없음)
- 파일 바이너리 검증은 비용이 크므로 상세 조회의 `verifyFiles=true`에서만 수행한다. 첨부파일과 서명 이미지의 `storage.image`를 다시 해시해 저장된 `sha256`과 비교한다.
- `MISMATCH`는 API를 거치지 않은 DB 직접 변경을 의미한다. 응답은 200으로 반환하되, `dataId`만 포함한 WARN 로그를 남긴다. 해당 기록에는 추가 서명을 거부하고(409 `INTEGRITY_MISMATCH`), 정정·무효화 기록은 생성할 수 있다.

### 4.5 행 잠금 및 트랜잭션

#### API별 잠금

| 작업 | 잠금 (획득 순서대로) |
|---|---|
| 프로젝트 수정·삭제, 참여자 추가·변경·제외 | `project` FOR UPDATE |
| 기록 생성 | `project` FOR SHARE (동시 프로젝트 삭제와 직렬화) |
| 기록 수정·삭제, 파일 업로드·삭제, 확인·서명 | `project` FOR SHARE → 대상 `data` FOR UPDATE |
| 정정·무효화 기록 생성 | `project` FOR SHARE → 원본 `data` FOR UPDATE → 새 `data` INSERT |
| AI 분석 결과 저장 (13.1 3단계) | `project` FOR SHARE → 대상 `data` FOR UPDATE |
| 조회·검색·다운로드 | 잠금 없음, `@Transactional(readOnly = true)` |

#### 구현 규칙

- **잠금 순서 고정:** 항상 `project` → `data`(원본) → `data`(대상) 순으로 잠가 교착 상태를 예방한다.
- **잠금 조회 방식:** Repository에 `@Lock(PESSIMISTIC_WRITE)`(FOR UPDATE), `@Lock(PESSIMISTIC_READ)`(FOR SHARE) 조회 메서드를 두고, 트랜잭션 안에서 **해당 행을 처음 읽을 때** 잠금 조회를 사용한다. 이미 영속성 컨텍스트에 있는 엔티티는 `EntityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE)`로 잠그고 최신 값을 다시 읽는다. (1차 캐시의 오래된 JSONB로 덮어쓰기 방지)
- **잠금 후 재검증:** 잠금 획득 후 삭제 여부, 서명 여부, 정정 여부, 참여자 권한, 중복을 다시 검사한다.
- **대기 시간:** `jakarta.persistence.lock.timeout=3000`. 초과(`PessimisticLockException`, `LockTimeoutException`)와 교착 상태(SQLState `40P01`)는 409 `CONCURRENT_MODIFICATION`으로 응답한다. 서버에서 자동 재시도하지 않는다.
- **파일 저장:** 이미지 검증과 SHA-256 계산은 잠금 전에 수행해 잠금 시간을 줄이고, `storage` INSERT와 `metadata.files` 갱신은 잠금을 잡은 같은 트랜잭션에서 처리한다. (고아 파일 방지)
- **외부 호출:** AI 서버 호출은 트랜잭션과 잠금 밖에서 수행한다. ([13.1](#131-ai-분석-요청결과-저장))

### 4.6 JSONB 형식 검증

- 저장 직전 Java 타입(record/class)으로 직렬화하므로 정의되지 않은 형식의 값은 저장되지 않는다. enum 값(`projectRole`, `status`, `kind` 등)도 Java enum으로 검증한다.
- 배열 크기 상한: `members` 200, `files` 20(삭제 포함 30), `confirmations` 50, `aiAnalyses` 30.

---

## 5. 인증 API

**Swagger Tag:** `Auth`

### 5.1 회원가입

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/auth/signup` |
| 인증 | 불필요 |
| 설명 | 계정을 생성한다. 관리자 승인 없이 즉시 활성화된다. 가입만으로는 어떤 프로젝트에도 접근할 수 없다. |

**Request Body**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| loginId | string | O | 4~100자, `^[A-Za-z0-9._-]+$` | 로그인 ID |
| password | string | O | 8~64자, 영문·숫자·특수문자 각 1자 이상 | 비밀번호 |
| name | string | O | 1~100자 | 이름 |
| roleLevel | integer | O | 1, 2, 3 | 계정 역할 |
| organization | string | X | 최대 255자 | 소속기관 |
| phone | string | X | 최대 20자, `^[0-9-]+$` | 전화번호 |

```json
{
  "loginId": "kim.site01",
  "password": "Passw0rd!",
  "name": "김현장",
  "roleLevel": 1,
  "organization": "OO건설",
  "phone": "010-1234-5678"
}
```

**Response `201 Created`** — `Location: /api/accounts/me`, `AccountResponse`

```json
{
  "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
  "loginId": "kim.site01",
  "name": "김현장",
  "roleLevel": 1,
  "roleName": "FIELD_WORKER",
  "organization": "OO건설",
  "phone": "010-1234-5678"
}
```

**예외**

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 필드 검증 실패 |
| 409 | DUPLICATE_LOGIN_ID | 이미 존재하는 loginId (동시 가입으로 인한 UNIQUE 위반 포함) |

**비즈니스 로직**

- 비밀번호는 `PasswordEncoder`(BCrypt)로 해시해 `password_hash`에 저장한다.
- 중복 사전 조회 후에도 `login_id` UNIQUE 위반(`DataIntegrityViolationException`)이 발생하면 409로 변환한다.
- 비밀번호·해시는 응답과 로그에 포함하지 않는다.

### 5.2 로그인

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/auth/login` |
| 인증 | 불필요 |

**Request Body**

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| loginId | string | O | NotBlank |
| password | string | O | NotBlank |

```json
{ "loginId": "kim.site01", "password": "Passw0rd!" }
```

**Response `200 OK`**

```json
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 7200,
  "account": {
    "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
    "loginId": "kim.site01",
    "name": "김현장",
    "roleLevel": 1,
    "roleName": "FIELD_WORKER",
    "organization": "OO건설",
    "phone": "010-1234-5678"
  }
}
```

**예외**

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 필드 누락 |
| 401 | LOGIN_FAILED | loginId 없음 또는 비밀번호 불일치 (구분하지 않음) |

---

## 6. 계정 API

**Swagger Tag:** `Account`

`AccountResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| uid | string(uuid) | 사용자 ID |
| loginId | string | 로그인 ID |
| name | string | 이름 |
| roleLevel | integer | 1·2·3 |
| roleName | string | FIELD_WORKER·CONTRACTOR·DEVELOPER |
| organization | string \| null | 소속기관 |
| phone | string \| null | 전화번호 |

### 6.1 내 계정 조회

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/accounts/me` |
| 인증 | 필요 |

**Response `200 OK`** — `AccountResponse`

| 상태 | 코드 | 조건 |
|---|---|---|
| 404 | ACCOUNT_NOT_FOUND | 토큰의 uid에 해당하는 계정 없음 |

### 6.2 내 계정 수정

| 항목 | 내용 |
|---|---|
| Method / URL | `PATCH /api/accounts/me` |
| 인증 | 필요 |
| 설명 | 이름·소속기관·전화번호 중 전달된 필드만 수정한다. |

**Request Body**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| name | string | X | 1~100자, 공백 불가 | 이름 |
| organization | string | X | 최대 255자 | 빈 문자열이면 null로 변경 |
| phone | string | X | 최대 20자, `^[0-9-]*$` | 빈 문자열이면 null로 변경 |

```json
{ "organization": "OO건설 품질팀", "phone": "010-9876-5432" }
```

**Response `200 OK`** — 수정된 `AccountResponse`

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 검증 실패, 변경 필드 없음 |
| 404 | ACCOUNT_NOT_FOUND | 계정 없음 |

- `loginId`, `roleLevel`은 변경할 수 없다. (요청 DTO에 필드가 없음)
- 확인·서명 이력의 확인자 이름·소속은 서명 당시 값으로 유지된다.

### 6.3 비밀번호 변경

| 항목 | 내용 |
|---|---|
| Method / URL | `PATCH /api/accounts/me/password` |
| 인증 | 필요 |

**Request Body**

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| currentPassword | string | O | NotBlank |
| newPassword | string | O | 회원가입 비밀번호 규칙 |

```json
{ "currentPassword": "Passw0rd!", "newPassword": "N3wPassw0rd!" }
```

**Response `204 No Content`**

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 비밀번호 규칙 위반 |
| 400 | SAME_AS_CURRENT_PASSWORD | 새 비밀번호가 현재와 동일 |
| 401 | INVALID_CURRENT_PASSWORD | 현재 비밀번호 불일치 |

- 이미 발급된 토큰은 만료 시까지 유효하다. ([17장](#17-현재-스키마의-제약-사항))

---

## 7. 프로젝트 API

**Swagger Tag:** `Project`

`ProjectResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| projectId | integer | 프로젝트 ID |
| name | string | 프로젝트 이름 (`pname`) |
| description | string \| null | 설명 |
| address | string \| null | 현장 주소 |
| startDate / endDate | string(date) \| null | 착공일 / 준공 예정일 |
| status | string | PLANNED·IN_PROGRESS·COMPLETED·SUSPENDED |
| owner | object | `{ uid, name, organization }` |
| myRole | string | 요청자의 프로젝트 역할 |
| memberCount | integer | OWNER 포함 참여자 수 |
| createdAt / updatedAt | string(datetime) | 생성 / 수정 시각 |

### 7.1 프로젝트 생성

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/projects` |
| 인증 | 필요 — roleLevel 2(시공사), 3(시행사) |

**Request Body**

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| name | string | O | 1~255자 |
| description | string | X | 최대 2000자 |
| address | string | X | 최대 255자 |
| startDate | string(date) | X | |
| endDate | string(date) | X | startDate 이후 |
| status | string | X | enum, 기본값 `PLANNED` |

```json
{
  "name": "OO빌딩 신축공사",
  "description": "지하 3층, 지상 15층 업무시설",
  "address": "서울특별시 강남구 테헤란로 123",
  "startDate": "2026-03-01",
  "endDate": "2027-08-31",
  "status": "IN_PROGRESS"
}
```

**Response `201 Created`** — `Location: /api/projects/1`

```json
{
  "projectId": 1,
  "name": "OO빌딩 신축공사",
  "description": "지하 3층, 지상 15층 업무시설",
  "address": "서울특별시 강남구 테헤란로 123",
  "startDate": "2026-03-01",
  "endDate": "2027-08-31",
  "status": "IN_PROGRESS",
  "owner": { "uid": "1f0e3c2a-6b7d-4c1e-9a2b-3c4d5e6f7a8b", "name": "이시공", "organization": "OO건설" },
  "myRole": "OWNER",
  "memberCount": 1,
  "createdAt": "2026-11-17T00:30:00Z",
  "updatedAt": "2026-11-17T00:30:00Z"
}
```

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 검증 실패, endDate < startDate |
| 403 | ROLE_NOT_ALLOWED | roleLevel 1(현장직) |

**비즈니스 로직**

- `pname` = name, `created_by` = 요청자 uid.
- `detail` = `{schemaVersion:1, description, address, startDate, endDate, status, createdAt, updatedAt, members: []}`

### 7.2 내 프로젝트 목록

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects` |
| 인증 | 필요 |
| 설명 | 요청자가 OWNER이거나 members에 포함된, 삭제되지 않은 프로젝트만 조회한다. |

**Query Parameter**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| keyword | string | X | 이름 부분 일치 (대소문자 무시) |
| status | string | X | 진행 상태 |
| page, size | integer | X | [1.5](#15-페이지네이션) |

**Response `200 OK`** — `ProjectResponse` 페이지 (projectId 내림차순)

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 잘못된 status·page·size |

**조회 조건 (Native Query)**

```sql
WHERE (p.created_by = :uid
       OR p.detail->'members' @> jsonb_build_array(jsonb_build_object('uid', CAST(:uid AS text))))
  AND p.detail->'deletion' IS NULL
```

### 7.3 프로젝트 상세

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects/{projectId}` |
| 인증 | 필요 — 참여자 |

**Response `200 OK`** — `ProjectResponse`

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND | 없음 또는 삭제됨 |

> 존재 여부 확인 후 참여자 여부를 검사한다. 삭제된 프로젝트는 참여자에게도 404다.

### 7.4 프로젝트 수정

| 항목 | 내용 |
|---|---|
| Method / URL | `PATCH /api/projects/{projectId}` |
| 인증 | 필요 — OWNER, MANAGER |

**Request Body** — 7.1과 같은 필드, 모두 선택

```json
{ "status": "COMPLETED", "endDate": "2027-09-15" }
```

**Response `200 OK`** — 수정된 `ProjectResponse`

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 검증 실패, 변경 필드 없음, 수정 후 endDate < startDate |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | MEMBER·VIEWER |
| 404 | PROJECT_NOT_FOUND | 없음 또는 삭제됨 |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

- `project` 행을 잠근 후 전달된 필드와 `updatedAt`·`updatedBy`만 갱신한다. `members`·`deletion`·`created_by`는 변경하지 않는다.

### 7.5 프로젝트 삭제 (Soft Delete)

| 항목 | 내용 |
|---|---|
| Method / URL | `DELETE /api/projects/{projectId}` |
| 인증 | 필요 — OWNER |

**Query Parameter**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| reason | string | X | 삭제 사유 (최대 500자) |

**Response `204 No Content`**

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | reason 길이 초과 |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | OWNER가 아님 |
| 404 | PROJECT_NOT_FOUND | 없음 또는 이미 삭제됨 |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

**비즈니스 로직**

- `project` 행을 잠그고 `detail.deletion = {deletedAt, deletedBy, reason}`만 추가한다.
- 소속 `data`·`storage` 행은 변경하지 않는다. 서명된 기록도 원본이 그대로 보존되며, 프로젝트 삭제로 조회에서만 제외된다.

---

## 8. 프로젝트 참여자 API

**Swagger Tag:** `Project Member`

`ProjectMemberResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| uid | string(uuid) | 사용자 ID |
| loginId | string | 로그인 ID |
| name | string | 이름 |
| organization | string \| null | 소속기관 |
| roleLevel / roleName | integer / string | 계정 역할 |
| projectRole | string | OWNER·MANAGER·MEMBER·VIEWER |
| addedAt | string(datetime) | 참여 시각 (OWNER는 프로젝트 생성 시각) |

> 전화번호는 포함하지 않는다. (개인정보 최소 노출)

### 8.1 참여자 목록

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects/{projectId}/members` |
| 인증 | 필요 — 참여자 |

**Response `200 OK`** — 배열 (OWNER 먼저, 이후 addedAt 오름차순)

```json
[
  {
    "uid": "1f0e3c2a-6b7d-4c1e-9a2b-3c4d5e6f7a8b",
    "loginId": "lee.contractor",
    "name": "이시공",
    "organization": "OO건설",
    "roleLevel": 2,
    "roleName": "CONTRACTOR",
    "projectRole": "OWNER",
    "addedAt": "2026-11-17T00:30:00Z"
  },
  {
    "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
    "loginId": "kim.site01",
    "name": "김현장",
    "organization": "OO건설",
    "roleLevel": 1,
    "roleName": "FIELD_WORKER",
    "projectRole": "MEMBER",
    "addedAt": "2026-11-17T00:35:00Z"
  }
]
```

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND | 없음 또는 삭제됨 |

- members의 uid에는 FK가 없으므로, 계정이 존재하지 않는 uid는 결과에서 제외하고 WARN 로그를 남긴다.

### 8.2 참여자 추가

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/projects/{projectId}/members` |
| 인증 | 필요 — OWNER, MANAGER |

**Request Body**

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| loginId | string | O | NotBlank |
| projectRole | string | O | `MANAGER` \| `MEMBER` \| `VIEWER` |

```json
{ "loginId": "kim.site01", "projectRole": "MEMBER" }
```

**Response `201 Created`** — `Location: /api/projects/1/members/{uid}`, `ProjectMemberResponse`

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 검증 실패, projectRole이 OWNER |
| 400 | MEMBER_LIMIT_EXCEEDED | 참여자 200명 초과 |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | MEMBER·VIEWER 요청, MANAGER가 MANAGER 지정 |
| 404 | PROJECT_NOT_FOUND | 없음 또는 삭제됨 |
| 404 | ACCOUNT_NOT_FOUND | loginId에 해당하는 계정 없음 |
| 409 | ALREADY_PROJECT_MEMBER | 이미 참여 중 (OWNER 포함) |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

**비즈니스 로직**

- 사용자 검색 API는 제공하지 않으며, loginId를 아는 경우에만 추가할 수 있다.
- `project` 행을 잠근 상태에서 중복 uid를 확인하고 `members`에 추가한다. (동시 추가로 인한 중복 방지)

### 8.3 참여자 권한 변경

| 항목 | 내용 |
|---|---|
| Method / URL | `PATCH /api/projects/{projectId}/members/{uid}` |
| 인증 | 필요 — OWNER, MANAGER |

**Request Body**

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| projectRole | string | O | `MANAGER` \| `MEMBER` \| `VIEWER` |

```json
{ "projectRole": "VIEWER" }
```

**Response `200 OK`** — `ProjectMemberResponse`

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 검증 실패, OWNER 지정 |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | MEMBER·VIEWER 요청, MANAGER가 MANAGER 지정 또는 다른 MANAGER 변경 |
| 404 | PROJECT_NOT_FOUND | 없음 또는 삭제됨 |
| 404 | MEMBER_NOT_FOUND | 대상이 참여자가 아님 |
| 409 | CANNOT_MODIFY_OWNER | 대상이 OWNER |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

> OWNER 위임 기능은 MVP 범위에서 제외한다.

### 8.4 참여자 제외 / 탈퇴

| 항목 | 내용 |
|---|---|
| Method / URL | `DELETE /api/projects/{projectId}/members/{uid}` |
| 인증 | 필요 — OWNER, MANAGER. 본인 uid이면 OWNER를 제외한 모든 참여자 |

**Response `204 No Content`**

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | 권한 부족 (MANAGER가 다른 MANAGER 제외 포함) |
| 404 | PROJECT_NOT_FOUND | 없음 또는 삭제됨 |
| 404 | MEMBER_NOT_FOUND | 대상이 참여자가 아님 |
| 409 | CANNOT_MODIFY_OWNER | 대상이 OWNER |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

- `members` 배열에서 항목을 제거한다. 제외된 사용자가 작성한 기록·파일·서명은 유지된다.

---

## 9. 시공 기록 API

**Swagger Tag:** `Construction Record`

`RecordResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| dataId | integer | 기록 ID |
| projectId | integer | 프로젝트 ID |
| type | string | PHOTO·INSPECTION·NOTE·INVALIDATION |
| title | string | 제목 |
| description | string \| null | 내용 |
| process | string \| null | 공정명 |
| capturedAt | string(datetime) | 시공·촬영 일시 |
| location | object \| null | [3.3 location](#하위-객체-정의) |
| referenceUrl | string \| null | 외부 참조 URL (`data.url`) |
| uploadedBy | object \| null | `{ uid, name, organization }` |
| files | array | 삭제되지 않은 `FileResponse` 목록 |
| status | string | ACTIVE·SUPERSEDED·INVALIDATED ([3.5](#35-기록-상태-계산값)) |
| locked | boolean | 서명되어 본문 변경이 불가능한지 여부 |
| confirmationStatus | string | `NONE` \| `APPROVED` \| `REJECTED` (최근 확인 기준) |
| confirmationCount | integer | 확인·서명 건수 |
| correction | object \| null | 이 기록이 정정·무효화 기록이면 `{ kind, originalDataId, rootDataId, reason, correctedBy{uid,name}, correctedAt }` |
| supersededByDataId | integer \| null | 이 기록을 정정한 기록 ID |
| invalidatedByDataId | integer \| null | 이 기록을 무효화한 기록 ID |
| latestAiAnalysis | object \| null | `{ analysisId, version, status, summary, completedAt }` |
| integrity | object | 서명 해시 검증 결과 `{ status, signedSnapshotSha256, currentSnapshotSha256, fileStatus, verifiedAt }` ([4.4](#44-서명-스냅샷-해시)) |
| createdAt / updatedAt | string(datetime) | 등록 / 수정 시각 |

### 9.1 시공 기록 생성

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/projects/{projectId}/records` |
| 인증 | 필요 — OWNER, MANAGER, MEMBER |
| 설명 | 시공 기록을 등록한다. 파일은 11.1 API로 업로드한다. |

**Request Body** (`RecordRequest`)

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| type | string | O | PHOTO·INSPECTION·NOTE (INVALIDATION 불가) |
| title | string | O | 1~200자 |
| description | string | X | 최대 5000자 |
| process | string | X | 최대 100자 |
| capturedAt | string(datetime) | O | 현재 시각 + 5분 이전 |
| location | object | X | location 검증 (위·경도는 함께 입력) |
| referenceUrl | string | X | http/https URL, 최대 2048자 |

```json
{
  "type": "PHOTO",
  "title": "3층 슬래브 철근 배근",
  "description": "D13 @200 배근 완료",
  "process": "철근콘크리트공사",
  "capturedAt": "2026-11-16T14:20:00+09:00",
  "location": {
    "site": "A동",
    "building": "본관",
    "floor": "3F",
    "zone": "X1-X3 / Y1-Y4",
    "latitude": 37.501274,
    "longitude": 127.039585
  }
}
```

**Response `201 Created`** — `Location: /api/projects/1/records/101`

```json
{
  "dataId": 101,
  "projectId": 1,
  "type": "PHOTO",
  "title": "3층 슬래브 철근 배근",
  "description": "D13 @200 배근 완료",
  "process": "철근콘크리트공사",
  "capturedAt": "2026-11-16T05:20:00Z",
  "location": {
    "site": "A동", "building": "본관", "floor": "3F", "zone": "X1-X3 / Y1-Y4",
    "address": null, "latitude": 37.501274, "longitude": 127.039585
  },
  "referenceUrl": null,
  "uploadedBy": { "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a", "name": "김현장", "organization": "OO건설" },
  "files": [],
  "status": "ACTIVE",
  "locked": false,
  "confirmationStatus": "NONE",
  "confirmationCount": 0,
  "correction": null,
  "supersededByDataId": null,
  "invalidatedByDataId": null,
  "latestAiAnalysis": null,
  "integrity": {
    "status": "NOT_SIGNED",
    "signedSnapshotSha256": null,
    "currentSnapshotSha256": null,
    "fileStatus": "NOT_CHECKED",
    "verifiedAt": "2026-11-17T00:40:00Z"
  },
  "createdAt": "2026-11-17T00:40:00Z",
  "updatedAt": "2026-11-17T00:40:00Z"
}
```

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 검증 실패, 미래 capturedAt, 위·경도 한쪽만 입력, type이 INVALIDATION |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | VIEWER |
| 404 | PROJECT_NOT_FOUND | 없음 또는 삭제됨 |

**비즈니스 로직**

- `project` 행을 FOR SHARE로 잠그고 삭제 여부를 확인한 뒤 `data` 행을 생성한다.
- metadata에 본문 필드, `schemaVersion`, `createdAt`, `updatedAt`, 빈 `files`·`confirmations`·`aiAnalyses`를 저장한다.

### 9.2 시공 기록 목록 (프로젝트 내 날짜·위치 필터)

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects/{projectId}/records` |
| 인증 | 필요 — 참여자 |

**Query Parameter**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| from | string(date) | X | capturedAt 시작일 (포함, KST 00:00) |
| to | string(date) | X | capturedAt 종료일 (포함, KST 23:59:59) |
| type | string | X | 기록 유형 |
| status | string | X | ACTIVE·SUPERSEDED·INVALIDATED |
| locked | boolean | X | 서명 여부 |
| process | string | X | 공정명 부분 일치 |
| site, building, zone | string | X | location 부분 일치 |
| floor | string | X | location.floor 일치 (대소문자 무시) |
| keyword | string | X | title·description 부분 일치 |
| uploadedBy | string(uuid) | X | 작성자 |
| confirmationStatus | string | X | NONE·APPROVED·REJECTED |
| sort | string | X | `capturedAt,desc`(기본) \| `capturedAt,asc` \| `createdAt,desc` \| `createdAt,asc` |
| page, size | integer | X | [1.5](#15-페이지네이션) |

**Response `200 OK`** — `RecordResponse` 페이지

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 날짜 형식 오류, from > to, 잘못된 enum·sort |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND | 없음 또는 삭제됨 |

**비즈니스 로직**

- 기본 조건: `project_id = :projectId AND metadata->'deletion' IS NULL`
- 날짜: `metadata->>'capturedAt' BETWEEN :fromUtc AND :toUtc` (UTC 고정 형식 문자열 비교)
- 위치: `metadata->'location'->>'floor' ILIKE :floor` 등. LIKE 검색어의 `%`, `_`, `\`는 이스케이프한다.
- `status` 필터와 응답의 상태값은 같은 프로젝트의 정정·무효화 기록(`metadata->'correction'->>'originalDataId'`)을 한 번에 조회해 계산한다. (N+1 방지)

### 9.3 시공 기록 상세

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects/{projectId}/records/{dataId}` |
| 인증 | 필요 — 참여자 |

**Query Parameter**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| verifyFiles | boolean | X | `true`면 첨부파일·서명 이미지 바이너리의 SHA-256까지 다시 계산해 `integrity.fileStatus`에 반영 (기본 `false`) |

**Response `200 OK`** — `RecordResponse`

서명된 기록의 `integrity` 예시 (`verifyFiles=true`)

```json
{
  "dataId": 101,
  "locked": true,
  "confirmationStatus": "APPROVED",
  "integrity": {
    "status": "VERIFIED",
    "signedSnapshotSha256": "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
    "currentSnapshotSha256": "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
    "fileStatus": "VERIFIED",
    "verifiedAt": "2026-11-18T05:00:00Z"
  }
}
```

(나머지 필드는 `RecordResponse`와 동일하여 생략)

**비즈니스 로직**

- 저장된 모든 확인 항목의 `recordSnapshotSha256`과 현재 데이터로 다시 계산한 해시를 비교한다. ([4.4](#44-서명-스냅샷-해시))
- 불일치(`MISMATCH`)여도 200으로 응답하고 WARN 로그를 남긴다.

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND | 없음 또는 삭제됨 |
| 404 | RECORD_NOT_FOUND | 없음, 삭제됨, 다른 프로젝트 소속 |

### 9.4 시공 기록 수정

| 항목 | 내용 |
|---|---|
| Method / URL | `PATCH /api/projects/{projectId}/records/{dataId}` |
| 인증 | 필요 — OWNER, MANAGER, 작성자 본인(MEMBER) |
| 설명 | **서명 전 기록만** 수정할 수 있다. 전달된 필드만 변경하며 `location`은 객체 전체를 교체한다. |

**Request Body** — `RecordRequest`와 같은 필드, 모두 선택 (type은 INVALIDATION 불가, INVALIDATION 기록의 type 변경 불가)

```json
{
  "description": "D13 @200 배근 완료, 피복두께 30mm 확인",
  "location": { "site": "A동", "building": "본관", "floor": "3F", "zone": "X1-X4 / Y1-Y4" }
}
```

**Response `200 OK`** — 수정된 `RecordResponse`

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 검증 실패, 변경 필드 없음 |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | VIEWER, 타인 기록을 수정하는 MEMBER |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |
| 409 | RECORD_LOCKED | 서명된 기록 → 10.1 정정 기록 사용 |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

**비즈니스 로직**

- `data` 행을 잠근 후 `confirmations`가 비어 있는지 다시 확인한다. (확인·서명과 동시에 수정되는 경우 방지)
- 본문 필드와 `updatedAt`·`updatedBy`만 갱신한다. `correction`은 변경할 수 없다.

### 9.5 시공 기록 삭제 (Soft Delete)

| 항목 | 내용 |
|---|---|
| Method / URL | `DELETE /api/projects/{projectId}/records/{dataId}` |
| 인증 | 필요 — OWNER, MANAGER, 작성자 본인(MEMBER) |
| 설명 | **서명 전 기록만** 삭제할 수 있다. 서명된 기록은 10.2 무효화 기록으로 처리한다. |

**Query Parameter**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| reason | string | X | 삭제 사유 (최대 500자) |

**Response `204 No Content`**

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | reason 길이 초과 |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | VIEWER, 타인 기록을 삭제하는 MEMBER |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 이미 삭제됨 |
| 409 | RECORD_LOCKED | 서명된 기록 |
| 409 | RECORD_HAS_CORRECTIONS | 이 기록을 참조하는 삭제되지 않은 정정·무효화 기록이 있음 (서명 전 기록은 정정 대상이 될 수 없으므로 정상적으로는 발생하지 않음) |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

**비즈니스 로직**

- `data` 행을 잠그고 `metadata.deletion = {deletedAt, deletedBy, reason}`만 추가한다. 다른 필드와 `storage` 행은 변경하지 않는다.
- 서명 전 정정 기록을 삭제하면 원본 기록은 다시 `ACTIVE` 상태가 되어 새 정정 기록을 만들 수 있다.

---

## 10. 정정·무효화 API

**Swagger Tag:** `Correction`

서명된 기록은 수정·삭제할 수 없다. 오류가 있으면 **원본 행을 변경하지 않고** 원본을 참조하는 새 기록을 생성한다.

```text
#101 원본 (서명됨, SUPERSEDED)
  └─ #120 정정 AMENDMENT (서명됨, SUPERSEDED)    correction.originalDataId = 101, rootDataId = 101
       └─ #135 정정 AMENDMENT (ACTIVE)           correction.originalDataId = 120, rootDataId = 101

#102 원본 (서명됨, INVALIDATED)
  └─ #121 무효화 INVALIDATION                    correction.originalDataId = 102, rootDataId = 102
```

**공통 규칙**

| 규칙 | 내용 |
|---|---|
| 대상 조건 | 삭제되지 않음, **서명됨**(`locked = true`), `ACTIVE` 상태(정정·무효화된 적 없음) |
| 체인 | 정정은 항상 체인의 최신 기록을 대상으로 한다. 원본 하나에 유효한 정정은 최대 1개 |
| 원본 보존 | 원본 행은 잠금만 하고 변경하지 않는다. 연결 관계는 새 기록의 `metadata.correction`에만 저장한다. |
| 중복 방지 | 원본 `data` 행 FOR UPDATE 잠금 상태에서 기존 정정·무효화 존재 여부를 확인한다. |
| 서명 전 대상 | 서명 전 기록은 9.4·9.5로 직접 수정·삭제한다. (`409 RECORD_NOT_LOCKED`) |

### 10.1 정정 기록 생성

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/projects/{projectId}/records/{dataId}/corrections` |
| 인증 | 필요 — OWNER, MANAGER, 원본 작성자 본인(MEMBER) |
| 설명 | `{dataId}`를 정정하는 새 기록(AMENDMENT)을 생성한다. |

**Request Body** — `RecordRequest` 전체 필드(필수 여부 동일) + `reason`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| reason | string | O | 1~1000자 | 정정 사유 |
| type, title, … | | | `RecordRequest`와 동일 | 정정된 기록 내용 |

```json
{
  "reason": "층 정보 오기재 (3F → 4F)",
  "type": "PHOTO",
  "title": "4층 슬래브 철근 배근",
  "description": "D13 @200 배근 완료",
  "process": "철근콘크리트공사",
  "capturedAt": "2026-11-16T14:20:00+09:00",
  "location": { "site": "A동", "building": "본관", "floor": "4F", "zone": "X1-X3 / Y1-Y4" }
}
```

**Response `201 Created`** — `Location: /api/projects/1/records/120`, 새 기록의 `RecordResponse`

```json
{
  "dataId": 120,
  "projectId": 1,
  "type": "PHOTO",
  "title": "4층 슬래브 철근 배근",
  "status": "ACTIVE",
  "locked": false,
  "files": [],
  "correction": {
    "kind": "AMENDMENT",
    "originalDataId": 101,
    "rootDataId": 101,
    "reason": "층 정보 오기재 (3F → 4F)",
    "correctedBy": { "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a", "name": "김현장" },
    "correctedAt": "2026-11-18T02:00:00Z"
  },
  "createdAt": "2026-11-18T02:00:00Z",
  "updatedAt": "2026-11-18T02:00:00Z"
}
```

(나머지 필드는 `RecordResponse`와 동일하여 생략)

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 검증 실패, reason 누락 |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | VIEWER, 원본 작성자가 아닌 MEMBER |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |
| 409 | RECORD_NOT_LOCKED | 서명 전 기록 |
| 409 | RECORD_ALREADY_CORRECTED | 이미 정정됨 (`message`에 최신 기록 ID 포함) |
| 409 | RECORD_INVALIDATED | 무효화된 기록 |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

**비즈니스 로직**

- 새 기록의 `uploaded_by` = 요청자, `correction.rootDataId` = 원본의 `correction.rootDataId` 또는 원본 `dataId`.
- 원본의 파일은 복사하지 않는다. 정정 기록의 파일은 11.1로 새로 업로드한다. (`storageId`가 하나의 기록에만 연결된다는 규칙 유지)
- 정정 기록은 일반 기록과 동일하게 수정·파일 업로드·서명할 수 있고, 서명되면 잠긴다.

### 10.2 무효화 기록 생성

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/projects/{projectId}/records/{dataId}/invalidation` |
| 인증 | 필요 — OWNER, MANAGER |
| 설명 | 서명된 기록을 더 이상 유효하지 않은 것으로 표시하는 무효화 기록(type `INVALIDATION`)을 생성한다. 서명된 기록 삭제 대신 사용한다. |

**Request Body**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| reason | string | O | 1~1000자 | 무효화 사유 |

```json
{ "reason": "다른 현장 사진이 잘못 등록됨" }
```

**Response `201 Created`** — `Location: /api/projects/1/records/121`, 무효화 기록의 `RecordResponse`

```json
{
  "dataId": 121,
  "projectId": 1,
  "type": "INVALIDATION",
  "title": "기록 #102 무효화",
  "description": "다른 현장 사진이 잘못 등록됨",
  "capturedAt": "2026-11-18T02:10:00Z",
  "location": { "site": "A동", "building": "본관", "floor": "3F", "zone": "X1-X3 / Y1-Y4", "address": null, "latitude": null, "longitude": null },
  "status": "ACTIVE",
  "locked": false,
  "files": [],
  "correction": {
    "kind": "INVALIDATION",
    "originalDataId": 102,
    "rootDataId": 102,
    "reason": "다른 현장 사진이 잘못 등록됨",
    "correctedBy": { "uid": "1f0e3c2a-6b7d-4c1e-9a2b-3c4d5e6f7a8b", "name": "이시공" },
    "correctedAt": "2026-11-18T02:10:00Z"
  },
  "createdAt": "2026-11-18T02:10:00Z",
  "updatedAt": "2026-11-18T02:10:00Z"
}
```

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | reason 누락·길이 초과 |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | MEMBER·VIEWER |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |
| 409 | RECORD_NOT_LOCKED | 서명 전 기록 → 9.5 삭제 사용 |
| 409 | RECORD_ALREADY_CORRECTED | 이미 정정된 기록 (체인의 최신 기록을 무효화해야 함) |
| 409 | RECORD_INVALIDATED | 이미 무효화됨 |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

**비즈니스 로직**

- 새 `data` 행: `type` = `INVALIDATION`, `uploaded_by` = 요청자, metadata에 `title`(`기록 #{dataId} 무효화`), `description`(= reason), `capturedAt`(= 현재 시각), `location`(원본 값 복사, 위치 검색용), `correction` 저장.
- 무효화 기록은 파일 업로드·정정·AI 분석 대상이 아니다. (`409 INVALID_RECORD_TYPE`) 확인·서명은 가능하다.
- 서명 전 무효화 기록은 9.5로 삭제(취소)할 수 있으며, 삭제되면 원본은 다시 `ACTIVE`가 된다.

### 10.3 정정 이력 조회

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects/{projectId}/records/{dataId}/history` |
| 인증 | 필요 — 참여자 |
| 설명 | `{dataId}`가 속한 정정 체인 전체(최초 원본 → 정정 → 무효화)를 반환한다. |

**Response `200 OK`**

```json
{
  "rootDataId": 101,
  "currentDataId": 135,
  "currentStatus": "ACTIVE",
  "items": [
    { "dataId": 101, "kind": "ORIGINAL", "title": "3층 슬래브 철근 배근", "status": "SUPERSEDED", "locked": true, "reason": null, "createdBy": { "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a", "name": "김현장" }, "createdAt": "2026-11-17T00:40:00Z" },
    { "dataId": 120, "kind": "AMENDMENT", "title": "4층 슬래브 철근 배근", "status": "SUPERSEDED", "locked": true, "reason": "층 정보 오기재 (3F → 4F)", "createdBy": { "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a", "name": "김현장" }, "createdAt": "2026-11-18T02:00:00Z" },
    { "dataId": 135, "kind": "AMENDMENT", "title": "4층 슬래브 철근 배근", "status": "ACTIVE", "locked": false, "reason": "공정명 보완", "createdBy": { "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a", "name": "김현장" }, "createdAt": "2026-11-19T01:00:00Z" }
  ]
}
```

| 필드 | 설명 |
|---|---|
| currentDataId | 체인의 최신 유효 기록 ID (무효화된 경우 무효화 기록 ID) |
| currentStatus | 체인의 최종 상태 (ACTIVE·INVALIDATED) |
| items[].kind | ORIGINAL·AMENDMENT·INVALIDATION |

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |

- 조회 조건: `dataId = rootDataId OR metadata->'correction'->>'rootDataId' = :rootDataId`, 삭제된 기록 제외, `createdAt` 오름차순.

---

## 11. 이미지 파일 API

**Swagger Tag:** `File`

> **MVP에서는 이미지 파일만 저장한다.** 영상 등 이미지가 아닌 파일의 업로드·저장·조회 기능은 제공하지 않는다. 이미지는 기존 `storage.image`(BYTEA)에 저장하며 DB 구조는 변경하지 않는다.

`FileResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| storageId | integer | 파일 ID |
| originalName | string | 원본 파일명 |
| contentType | string | 서버가 판별한 MIME 타입 (`image/jpeg` \| `image/png`) |
| size | integer | 바이트 |
| width / height | integer | 이미지 픽셀 크기 |
| sha256 | string | SHA-256 해시 (64자리 hex) |
| downloadUrl | string | `/api/files/{storageId}` |
| uploadedBy | string(uuid) | 업로드 사용자 |
| uploadedAt | string(datetime) | 업로드 시각 |

### 11.0 이미지 파일 정책

| 구분 | 허용 형식 | 최대 크기 | 픽셀 제한 |
|---|---|---|---|
| 현장 사진 | JPEG, PNG | 10MB / 파일 | 가로·세로 각 1~10,000px, 총 4,000만 픽셀 이하 |
| 서명 이미지 | JPEG, PNG | 1MB | 가로·세로 각 1~4,000px |
| 기록당 사진 | 삭제되지 않은 사진 20장 (삭제 포함 30장) | | |
| 요청당 사진 | 10장, 요청 합계 100MB | | |

- WebP·HEIC·GIF·영상 등은 허용하지 않는다. (JDK 기본 `ImageIO`로 검증 가능한 형식으로 한정해 추가 라이브러리 없이 구현)
- `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=100MB`로 1차 제한하고, Service에서 용도별 크기를 다시 검증한다.
- MVP 기간에는 업로드 파일을 자동 삭제하지 않는다. (보관 기간 정책 없음)

#### 검증 순서 (`ImageFileValidator`)

| 순서 | 검증 | 실패 시 |
|---|---|---|
| 1 | 빈 파일 여부 | 400 `INVALID_INPUT` |
| 2 | 바이트 크기 (용도별 상한) | 413 `FILE_TOO_LARGE` |
| 3 | **매직 바이트**로 실제 형식 판별 — JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`. 클라이언트가 보낸 Content-Type은 사용하지 않음 | 415 `UNSUPPORTED_FILE_TYPE` |
| 4 | 확장자(`.jpg`, `.jpeg`, `.png`, 대소문자 무시)와 판별 형식 일치 | 415 `UNSUPPORTED_FILE_TYPE` |
| 5 | `ImageIO` 리더로 **이미지 헤더 파싱** (픽셀 전체를 디코딩하지 않음) — 손상·위장 파일 차단 | 400 `INVALID_IMAGE` |
| 6 | 가로·세로·총 픽셀 수 제한 (압축 폭탄 방지) | 400 `INVALID_IMAGE` |
| 7 | SHA-256 계산 | — |
| 8 | 같은 기록·같은 요청 내 동일 해시 중복 ([4.3](#43-중복-방지-unique-대체)) | 409 `DUPLICATE_FILE` |

- 원본 파일명은 경로 문자(`/`, `\`, `..`)와 제어 문자를 제거하고 255자로 자른 뒤 `storage.name`에 저장한다.
- 원본 바이너리는 변환·재압축하지 않고 그대로 저장한다. (증빙 원본 보존, SHA-256 일치 유지) 따라서 EXIF(촬영 일시·GPS 등)도 그대로 보존된다.

### 11.1 파일 업로드

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/projects/{projectId}/records/{dataId}/files` |
| Content-Type | `multipart/form-data` |
| 인증 | 필요 — OWNER, MANAGER, 기록 작성자 본인(MEMBER) |

**Request (multipart)**

| 파트 | 타입 | 필수 | 설명 |
|---|---|---|---|
| files | file[] | O | JPEG·PNG 이미지 1~10개 |

Swagger에서 `@Schema(type = "string", format = "binary")` 배열로 문서화하여 UI에서 파일을 선택할 수 있게 한다.

**Response `201 Created`** — `FileResponse` 배열

```json
[
  {
    "storageId": 15,
    "originalName": "slab_rebar_01.jpg",
    "contentType": "image/jpeg",
    "size": 2483021,
    "width": 4032,
    "height": 3024,
    "sha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
    "downloadUrl": "/api/files/15",
    "uploadedBy": "8a6e0804-2bd0-4672-b79d-d97027f9071a",
    "uploadedAt": "2026-11-17T00:41:00Z"
  }
]
```

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 파일 없음, 빈 파일, 요청당 개수 초과 |
| 400 | INVALID_IMAGE | 손상된 이미지, 헤더 파싱 실패, 픽셀 제한 초과 |
| 400 | FILE_COUNT_EXCEEDED | 기록당 사진 수 초과 |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | VIEWER, 타인 기록의 MEMBER |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |
| 409 | RECORD_LOCKED | 서명된 기록 |
| 409 | INVALID_RECORD_TYPE | INVALIDATION 기록 |
| 409 | DUPLICATE_FILE | 같은 기록에 동일한 이미지(SHA-256 일치)가 이미 있거나 요청 내 중복 |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |
| 413 | FILE_TOO_LARGE | 크기 초과 (`MaxUploadSizeExceededException` 포함) |
| 415 | UNSUPPORTED_FILE_TYPE | JPEG·PNG가 아님 (영상 포함), 확장자와 실제 형식 불일치 |

**비즈니스 로직 (단일 트랜잭션, 전체 성공 또는 전체 실패)**

1. 잠금 전에 모든 파일을 [11.0 검증 순서](#검증-순서-imagefilevalidator) 1~7단계로 검증하고, 요청 내 중복 해시를 확인한다.
2. `project` FOR SHARE → `data` FOR UPDATE 순으로 잠그고 삭제 여부·서명 여부·사진 수·기존 파일과의 해시 중복을 다시 확인한다.
3. `storage`에 `name`·`image`를 INSERT하고, 생성된 `storageId`로 `metadata.files`에 메타데이터를 추가한다.
4. 하나라도 실패하면 이미 INSERT한 `storage` 행을 포함해 전체 롤백한다.

### 11.2 파일 목록

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects/{projectId}/records/{dataId}/files` |
| 인증 | 필요 — 참여자 |

**Response `200 OK`** — 삭제되지 않은 `FileResponse` 배열 (uploadedAt 오름차순)

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |

### 11.3 파일 다운로드

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/files/{storageId}` |
| 인증 | 필요 — 파일이 속한 프로젝트의 참여자 (서명 이미지 포함) |

**Query Parameter**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| download | boolean | X | `true`면 `attachment`, 기본 `inline` |

**Response `200 OK`** — 바이너리

| 헤더 | 값 |
|---|---|
| Content-Type | 저장된 contentType |
| Content-Length | 파일 크기 |
| Content-Disposition | `inline; filename*=UTF-8''{인코딩된 원본 파일명}` |
| ETag | `"{sha256}"` |
| Cache-Control | `private, max-age=3600` |
| X-Content-Type-Options | `nosniff` |

`If-None-Match`가 ETag와 같으면 `304 Not Modified`를 반환한다.

| 상태 | 코드 | 조건 (오류 시 JSON 응답) |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 소속 프로젝트의 참여자가 아님 |
| 404 | FILE_NOT_FOUND | storage 행 없음, 어떤 기록에도 연결되지 않음, 파일·기록·프로젝트가 삭제됨 |

**비즈니스 로직**

- 소속 기록 조회: `metadata->'files' @> '[{"storageId": :id}]'` 또는 `metadata->'confirmations' @> '[{"signature": {"storageId": :id}}]'`
- 연결된 기록이 없는 storage 행은 권한 검증이 불가능하므로 404로 응답한다.
- `storage`에는 형식 정보가 없으므로 Content-Type은 `metadata.files[].contentType`(서명은 `signature.contentType`)을 사용한다.

### 11.4 파일 삭제 (Soft Delete)

| 항목 | 내용 |
|---|---|
| Method / URL | `DELETE /api/projects/{projectId}/records/{dataId}/files/{storageId}` |
| 인증 | 필요 — OWNER, MANAGER, 기록 작성자 본인(MEMBER) |

**Response `204 No Content`**

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | VIEWER, 타인 기록의 MEMBER |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |
| 404 | FILE_NOT_FOUND | 해당 기록의 파일이 아니거나 이미 삭제됨 |
| 409 | RECORD_LOCKED | 서명된 기록 |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |

- `data` 행을 잠그고 해당 `files[]` 항목에 `deletion`을 추가한다. `storage` 행은 유지한다.
- AI 분석 이력의 `targetStorageIds`는 그대로 유지한다.

---

## 12. 담당자 확인·서명 API

**Swagger Tag:** `Confirmation`

`ConfirmationResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| confirmationId | string(uuid) | 확인 ID |
| confirmer | object | `{ uid, name, organization, roleLevel, roleName }` (서명 당시 값) |
| status | string | APPROVED·REJECTED |
| comment | string \| null | 의견 |
| signature | object | `{ storageId, downloadUrl, sha256 }` |
| recordSnapshotSha256 | string | 서명 시점 본문 해시 |
| confirmedAt | string(datetime) | 서버 시각 |

### 12.1 확인·서명 등록

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/projects/{projectId}/records/{dataId}/confirmations` |
| Content-Type | `multipart/form-data` |
| 인증 | 필요 — roleLevel 2·3 이면서 프로젝트 참여자 (작성자 본인 제외) |

**Request (multipart)**

| 파트 | 타입 | 필수 | 설명 |
|---|---|---|---|
| request | JSON (`application/json`) | O | 아래 표 |
| signature | file | O | PNG·JPEG, 최대 1MB |

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| status | string | O | APPROVED·REJECTED |
| comment | string | 조건부 | 최대 1000자, REJECTED이면 필수 |

```json
{ "status": "APPROVED", "comment": "배근 간격 확인 완료" }
```

**Response `201 Created`**

```json
{
  "confirmationId": "c2f1a9b0-5d3e-4f7a-8b1c-2d3e4f5a6b7c",
  "confirmer": {
    "uid": "1f0e3c2a-6b7d-4c1e-9a2b-3c4d5e6f7a8b",
    "name": "김감독",
    "organization": "OO개발",
    "roleLevel": 3,
    "roleName": "DEVELOPER"
  },
  "status": "APPROVED",
  "comment": "배근 간격 확인 완료",
  "signature": {
    "storageId": 16,
    "downloadUrl": "/api/files/16",
    "sha256": "3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b"
  },
  "recordSnapshotSha256": "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
  "confirmedAt": "2026-11-17T01:00:00Z"
}
```

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 검증 실패, REJECTED인데 comment 없음 |
| 400 | CONFIRMATION_LIMIT_EXCEEDED | 확인 50건 초과 |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | ROLE_NOT_ALLOWED | roleLevel 1(현장직) |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |
| 409 | SELF_CONFIRMATION_NOT_ALLOWED | 본인이 작성한 기록 |
| 409 | RECORD_HAS_NO_FILES | 사진이 없는 PHOTO 기록 |
| 409 | RECORD_SUPERSEDED | 이미 정정된 기록 (최신 정정 기록에 서명해야 함) |
| 409 | RECORD_INVALIDATED | 무효화된 기록 |
| 409 | DUPLICATE_CONFIRMATION | 같은 확인자가 직전 본인 확인과 같은 status로 재등록 |
| 409 | INTEGRITY_MISMATCH | 첨부 이미지 해시 불일치, 또는 기존 서명 해시와 현재 데이터 불일치 |
| 409 | CONCURRENT_MODIFICATION | 잠금 대기 시간 초과 |
| 400 | INVALID_IMAGE | 서명 이미지 손상·픽셀 제한 초과 |
| 413 | FILE_TOO_LARGE | 서명 이미지 1MB 초과 |
| 415 | UNSUPPORTED_FILE_TYPE | 서명 이미지가 JPEG·PNG가 아님 |

**비즈니스 로직 (단일 트랜잭션)**

1. 잠금 전에 서명 이미지를 [11.0 검증 순서](#검증-순서-imagefilevalidator)로 검증하고 SHA-256을 계산한다.
2. `project` FOR SHARE → `data` FOR UPDATE 순으로 잠그고 삭제·정정·무효화 상태와 중복 확인 여부를 다시 확인한다.
3. 삭제되지 않은 첨부 이미지의 `storage.image` 해시를 다시 계산해 `files[].sha256`과 비교하고, 기존 서명이 있으면 스냅샷 해시도 비교한다. 불일치하면 409 `INTEGRITY_MISMATCH`. ([4.4](#44-서명-스냅샷-해시))
4. `storage`에 서명 이미지를 저장한다.
5. 서명 시점의 기록 데이터로 `recordSnapshotSha256`을 계산해 `snapshotVersion`과 함께 `confirmations`에 추가한다. 확인자 이름·소속·역할은 당시 값으로 저장한다.
6. 확인은 **추가 전용**이다. 수정·삭제 API가 없으며, 결과를 바꾸려면 새 확인을 등록한다. 가장 최근 확인이 `confirmationStatus`가 된다.
7. 확인이 1건 이상이면 기록은 `locked`가 되어 본문 수정, 삭제, 이미지 업로드·삭제가 불가능하다. (REJECTED여도 잠김) 오류가 있으면 원본을 수정하지 않고 10.1 정정 기록 또는 10.2 무효화 기록을 생성한다.

### 12.2 확인·서명 이력 조회

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects/{projectId}/records/{dataId}/confirmations` |
| 인증 | 필요 — 참여자 |

**Response `200 OK`** — `ConfirmationResponse` 배열 (confirmedAt 오름차순)

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |

---

## 13. AI 분석 API

**Swagger Tag:** `AI Analysis`

**처리 방식 (MVP):** 클라이언트가 분석을 요청하면 **Spring Boot 서버가 AI 서버를 동기 호출**하고, 응답받은 결과를 `data.metadata.aiAnalyses`에 직접 저장한다. AI 서버가 Spring Boot API를 호출하지 않으므로 AI 전용 서비스 토큰은 MVP에서 구현하지 않는다.

```text
Client ──POST /ai-analyses──▶ Spring Boot ──(파일 전송)──▶ AI Server
                                  │◀────────분석 결과────────┘
                                  └─ data.metadata.aiAnalyses 에 추가 저장 ─▶ 201 응답
```

`AiAnalysisResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| analysisId | string(uuid) | 분석 ID |
| version | integer | 기록 내 순번 (1부터) |
| status | string | SUCCESS·FAILED |
| model | string \| null | AI 서버가 반환한 모델명·버전 |
| targetStorageIds | integer[] | 분석 대상 파일 |
| summary | string \| null | 요약 |
| result | object \| null | 분석 결과 JSON |
| errorCode | string \| null | AI_SERVER_ERROR·AI_SERVER_TIMEOUT·AI_INVALID_RESPONSE |
| errorMessage | string \| null | 실패 사유 (내부 정보 제거) |
| requestedBy | object | `{ uid, name }` |
| requestedAt | string(datetime) | 요청 시각 |
| completedAt | string(datetime) | 결과 저장 시각 |

### 13.0 AI 서버 연동 규칙

| 항목 | 내용 |
|---|---|
| 설정 | `ai.server.base-url`, `ai.server.connect-timeout=5s`, `ai.server.read-timeout=60s` (환경변수 주입) |
| 클라이언트 | Spring 기본 `RestClient` 사용 (추가 라이브러리 없음) |
| 인터페이스 | `AiAnalysisClient` 인터페이스로 분리 — 테스트에서 Mock 구현으로 교체, 향후 비동기 구현으로 교체 가능 |
| 요청 (가정) | `POST {base-url}/analyze`, multipart: `images[]`, `context`(JSON: dataId, process, type) |
| 응답 (가정) | `{ "model": string, "summary": string, "result": object }` |
| 응답 검증 | model·result 필수, result 직렬화 크기 256KB 이하. 위반 시 `AI_INVALID_RESPONSE`로 FAILED 처리 |

> **확인 필요:** AI 서버의 실제 요청·응답 형식은 아직 정해지지 않았다. 위 형식은 가정이며, `AiAnalysisClient` 구현체 안에서만 변환하므로 확정되더라도 본 API 명세는 바뀌지 않는다.

### 13.1 AI 분석 요청·결과 저장

| 항목 | 내용 |
|---|---|
| Method / URL | `POST /api/projects/{projectId}/records/{dataId}/ai-analyses` |
| 인증 | 필요 — OWNER, MANAGER, MEMBER |

**Request Body**

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| targetStorageIds | integer[] | X | 1~5개, 해당 기록의 삭제되지 않은 **이미지** 파일 | 미입력 시 최근 업로드 이미지 최대 5개 |

```json
{ "targetStorageIds": [15] }
```

**Response `201 Created`** — `Location: /api/projects/1/records/101/ai-analyses/latest`, `AiAnalysisResponse`

```json
{
  "analysisId": "e1d2c3b4-a5f6-4789-90ab-cdef01234567",
  "version": 1,
  "status": "SUCCESS",
  "model": "rebar-detector-v2",
  "targetStorageIds": [15],
  "summary": "철근 간격 평균 198mm, 기준 충족",
  "result": {
    "rebarSpacingMm": 198,
    "detectedObjects": [ { "label": "rebar", "confidence": 0.97, "bbox": [120, 80, 640, 92] } ],
    "defects": []
  },
  "errorCode": null,
  "errorMessage": null,
  "requestedBy": { "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a", "name": "김현장" },
  "requestedAt": "2026-11-17T00:44:50Z",
  "completedAt": "2026-11-17T00:45:10Z"
}
```

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 6개 이상, 기록에 없는·삭제된·이미지가 아닌 storageId |
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | VIEWER |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |
| 409 | NO_ANALYZABLE_FILES | 분석할 이미지 파일이 없음 |
| 409 | INVALID_RECORD_TYPE | INVALIDATION 기록 |
| 409 | RECORD_INVALIDATED | 무효화된 기록 |
| 409 | AI_ANALYSIS_LIMIT_EXCEEDED | 분석 이력 30건 초과 |
| 409 | CONCURRENT_MODIFICATION | 결과 저장 시 잠금 대기 시간 초과 |
| 502 | AI_SERVER_ERROR | AI 서버 오류 응답·연결 실패·잘못된 응답 (FAILED 이력 저장됨) |
| 504 | AI_SERVER_TIMEOUT | AI 서버 응답 시간 초과 (FAILED 이력 저장됨) |

**비즈니스 로직 — DB 잠금을 쥔 채 외부 호출을 하지 않도록 3단계로 분리**

1. **사전 검증 (읽기 전용 트랜잭션):** 권한, 기록 상태, 대상 파일, 이력 건수를 확인하고 대상 이미지 바이너리를 읽는다.
2. **AI 호출 (트랜잭션 밖):** `AiAnalysisClient`로 AI 서버를 호출한다. DB 커넥션과 잠금을 점유하지 않는다.
3. **결과 저장 (쓰기 트랜잭션):** `data` 행을 잠그고 삭제 여부를 다시 확인한 뒤, `version = 기존 최대 version + 1`로 `aiAnalyses`에 추가한다. 실패한 호출도 `FAILED` 이력으로 저장한 뒤 502·504를 응답한다.
   - 1~2단계 사이에 기록이 삭제되었으면 결과를 저장하지 않고 404를 반환한다.
   - 같은 기록에 동시에 요청하면 AI 호출은 병렬로 실행되고, 저장 단계에서 잠금으로 직렬화되어 version이 중복되지 않는다.
- 분석 이력은 추가 전용이며 수정·삭제 API가 없다.
- **서명된 기록에도 분석을 추가할 수 있다.** `aiAnalyses`는 서명 보호 대상인 본문 필드가 아니며, 스냅샷 해시에 포함되지 않는다.

**향후 비동기 확장 (MVP 미구현)**

- 요청 시 `status: PENDING` 이력을 먼저 저장하고 `202 Accepted`를 반환한다.
- AI 서버가 `POST /api/internal/ai-analyses/{analysisId}/result`를 **AI 전용 서비스 토큰**으로 호출해 결과를 저장한다.
- 사용자 JWT와 분리된 별도 인증 필터를 사용하며, `aiAnalyses` JSON 구조와 조회 API(13.2·13.3)는 변경하지 않는다.

### 13.2 AI 분석 이력 조회

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects/{projectId}/records/{dataId}/ai-analyses` |
| 인증 | 필요 — 참여자 |

**Response `200 OK`** — `AiAnalysisResponse` 배열 (version 내림차순)

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |

### 13.3 최신 AI 분석 조회

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/projects/{projectId}/records/{dataId}/ai-analyses/latest` |
| 인증 | 필요 — 참여자 |

**Query Parameter**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| successOnly | boolean | X | `true`(기본)면 최신 SUCCESS, `false`면 상태 무관 최신 |

**Response `200 OK`** — `AiAnalysisResponse`

| 상태 | 코드 | 조건 |
|---|---|---|
| 403 | PROJECT_ACCESS_DENIED | 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND / RECORD_NOT_FOUND | 대상 없음 또는 삭제됨 |
| 404 | AI_ANALYSIS_NOT_FOUND | 조건에 맞는 분석 결과 없음 |

---

## 14. 시공 이력 검색 API

**Swagger Tag:** `Search`

### 14.1 시공 이력 통합 검색

| 항목 | 내용 |
|---|---|
| Method / URL | `GET /api/records/search` |
| 인증 | 필요 |
| 설명 | 요청자가 참여 중인 삭제되지 않은 모든 프로젝트(또는 지정 프로젝트)의 시공 기록을 날짜·위치 조건으로 검색한다. |

**Query Parameter**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| projectId | integer | X | 특정 프로젝트로 한정 (참여 중이어야 함) |
| from / to | string(date) | X | capturedAt 기간 (포함, KST 기준, 최대 366일) |
| address | string | X | location.address 부분 일치 |
| site, building, floor, zone | string | X | 9.2와 동일 |
| latitude / longitude | number | 조건부 | 반경 검색 중심 (radiusMeters와 함께) |
| radiusMeters | integer | 조건부 | 1~5000 |
| type | string | X | 기록 유형 |
| status | string | X | ACTIVE·SUPERSEDED·INVALIDATED |
| keyword | string | X | title·description·process 부분 일치 |
| hasAiAnalysis | boolean | X | SUCCESS 분석 결과 존재 여부 |
| confirmationStatus | string | X | NONE·APPROVED·REJECTED |
| sort | string | X | 9.2와 동일. 반경 검색 시 `distance,asc` 추가 허용 |
| page, size | integer | X | [1.5](#15-페이지네이션) |

- 검색 조건이 없으면 최근 기록 순으로 반환한다.
- `from`만 있으면 `from`부터 366일까지, `to`만 있으면 `to` 이전 366일까지로 제한한다.

**Response `200 OK`** — `RecordSearchResponse` 페이지

`RecordSearchResponse` = `RecordResponse`에서 `files` 대신 `thumbnail`(첫 번째 이미지 `{storageId, downloadUrl}` 또는 null)·`fileCount`를 반환하고, `projectName`·`distanceMeters`(반경 검색 시)를 추가한다.

```json
{
  "content": [
    {
      "dataId": 135,
      "projectId": 1,
      "projectName": "OO빌딩 신축공사",
      "type": "PHOTO",
      "title": "4층 슬래브 철근 배근",
      "process": "철근콘크리트공사",
      "capturedAt": "2026-11-16T05:20:00Z",
      "location": { "site": "A동", "building": "본관", "floor": "4F", "zone": "X1-X3 / Y1-Y4", "address": null, "latitude": 37.501274, "longitude": 127.039585 },
      "distanceMeters": 42.7,
      "uploadedBy": { "uid": "8a6e0804-2bd0-4672-b79d-d97027f9071a", "name": "김현장", "organization": "OO건설" },
      "thumbnail": { "storageId": 31, "downloadUrl": "/api/files/31" },
      "fileCount": 2,
      "status": "ACTIVE",
      "locked": true,
      "confirmationStatus": "APPROVED",
      "correction": { "kind": "AMENDMENT", "originalDataId": 120, "rootDataId": 101 },
      "latestAiAnalysis": { "analysisId": "a7b8c9d0-1e2f-4a3b-8c4d-5e6f7a8b9c0d", "version": 1, "status": "SUCCESS", "summary": "철근 간격 평균 199mm, 기준 충족", "completedAt": "2026-11-19T01:10:00Z" },
      "createdAt": "2026-11-19T01:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

| 상태 | 코드 | 조건 |
|---|---|---|
| 400 | INVALID_INPUT | 날짜 형식 오류, from > to, 기간 366일 초과, 반경 파라미터 일부만 입력, 범위 초과 |
| 403 | PROJECT_ACCESS_DENIED | projectId 지정 시 참여자가 아님 |
| 404 | PROJECT_NOT_FOUND | projectId 지정 시 없음 또는 삭제됨 |

**비즈니스 로직**

- 접근 범위 조건을 항상 적용한다: `data.project_id IN (요청자가 OWNER 또는 members인, 삭제되지 않은 프로젝트)` AND `data.metadata->'deletion' IS NULL`
- 반경 검색: 위·경도 bounding box로 1차 필터 후 Haversine 공식으로 거리를 계산한다. (`(metadata->'location'->>'latitude')::double precision`, PostGIS 미사용)
- 인덱스를 추가하지 않으므로 JSONB 조건은 순차 스캔된다. 프로젝트 범위 조건(`idx_data_project_id`)으로 대상 행을 먼저 줄인다.

---

## 15. 오류 코드 목록

| HTTP | 코드 | 설명 |
|---|---|---|
| 400 | INVALID_INPUT | 요청 값 검증 실패 |
| 400 | SAME_AS_CURRENT_PASSWORD | 새 비밀번호가 기존과 동일 |
| 400 | FILE_COUNT_EXCEEDED | 기록당 사진 개수 초과 |
| 400 | INVALID_IMAGE | 손상된 이미지, 헤더 파싱 실패, 픽셀 제한 초과 |
| 400 | MEMBER_LIMIT_EXCEEDED | 참여자 수 초과 |
| 400 | CONFIRMATION_LIMIT_EXCEEDED | 확인 건수 초과 |
| 401 | UNAUTHORIZED | 토큰 없음·형식 오류·위조 |
| 401 | TOKEN_EXPIRED | 토큰 만료 |
| 401 | LOGIN_FAILED | 로그인 ID 또는 비밀번호 불일치 |
| 401 | INVALID_CURRENT_PASSWORD | 현재 비밀번호 불일치 |
| 403 | ROLE_NOT_ALLOWED | 계정 역할상 허용되지 않음 |
| 403 | PROJECT_ACCESS_DENIED | 프로젝트 참여자가 아님 |
| 403 | INSUFFICIENT_PROJECT_ROLE | 프로젝트 역할 권한 부족 |
| 404 | ACCOUNT_NOT_FOUND | 계정 없음 |
| 404 | PROJECT_NOT_FOUND | 프로젝트 없음 또는 삭제됨 |
| 404 | MEMBER_NOT_FOUND | 프로젝트 참여자 아님 |
| 404 | RECORD_NOT_FOUND | 시공 기록 없음 또는 삭제됨 |
| 404 | FILE_NOT_FOUND | 파일 없음 또는 삭제됨 |
| 404 | AI_ANALYSIS_NOT_FOUND | AI 분석 결과 없음 |
| 405 | METHOD_NOT_ALLOWED | 지원하지 않는 HTTP Method |
| 409 | DUPLICATE_LOGIN_ID | 로그인 ID 중복 |
| 409 | ALREADY_PROJECT_MEMBER | 이미 프로젝트 참여자 |
| 409 | CANNOT_MODIFY_OWNER | OWNER 변경·제외 불가 |
| 409 | RECORD_LOCKED | 서명된 기록의 본문·파일 변경, 삭제 시도 |
| 409 | RECORD_NOT_LOCKED | 서명 전 기록에 정정·무효화 시도 |
| 409 | RECORD_ALREADY_CORRECTED | 이미 정정된 기록 |
| 409 | RECORD_SUPERSEDED | 정정된 이전 버전 기록에 서명 시도 |
| 409 | RECORD_INVALIDATED | 무효화된 기록 |
| 409 | RECORD_HAS_CORRECTIONS | 정정·무효화 기록이 참조 중 |
| 409 | RECORD_HAS_NO_FILES | 사진 없는 PHOTO 기록 서명 시도 |
| 409 | INVALID_RECORD_TYPE | 무효화 기록에 허용되지 않는 작업 |
| 409 | SELF_CONFIRMATION_NOT_ALLOWED | 본인 기록 확인 불가 |
| 409 | DUPLICATE_CONFIRMATION | 같은 확인자의 동일 status 연속 등록 |
| 409 | DUPLICATE_FILE | 같은 기록에 동일 이미지(SHA-256) 중복 연결 |
| 409 | INTEGRITY_MISMATCH | 저장된 해시와 현재 데이터·이미지 해시 불일치 |
| 409 | NO_ANALYZABLE_FILES | 분석할 이미지 없음 |
| 409 | AI_ANALYSIS_LIMIT_EXCEEDED | 분석 이력 건수 초과 |
| 409 | CONCURRENT_MODIFICATION | 동시 수정으로 잠금 대기 시간 초과 또는 교착 상태 |
| 413 | FILE_TOO_LARGE | 파일 크기 초과 |
| 413 | PAYLOAD_TOO_LARGE | 요청 본문 크기 초과 |
| 415 | UNSUPPORTED_FILE_TYPE | JPEG·PNG가 아닌 파일 (영상 포함), 확장자 불일치 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 지원하지 않는 요청 Content-Type |
| 500 | INTERNAL_ERROR | 서버 내부 오류 |
| 502 | AI_SERVER_ERROR | AI 서버 오류·잘못된 응답 |
| 504 | AI_SERVER_TIMEOUT | AI 서버 응답 시간 초과 |

---

## 16. MVP 결정 사항

| # | 주제 | 결정 | 반영 위치 |
|---|---|---|---|
| D0 | DB 스키마 | 4개 테이블의 테이블·컬럼·제약조건·인덱스를 변경하지 않음. JSONB 활용 | 3장 |
| D1 | 회원가입 승인 | 승인 없이 가입 허용. 프로젝트 데이터 접근은 `created_by` 또는 `detail.members` 참여자만 가능 | 2장, 5.1 |
| D2 | AI 결과 저장 | Spring Boot가 AI 서버를 동기 호출하고 결과를 `data.metadata.aiAnalyses`에 직접 저장. AI 서비스 토큰은 미구현, 비동기 확장 구조만 고려 | 13장 |
| D3 | 서명 기록 오류 | 서명 기록 수정·삭제 불가. 원본을 참조하는 정정 기록(AMENDMENT) 생성. 연결·사유·작성자·시간은 `metadata.correction`에 저장 | 10장 |
| D4 | 파일 | **이미지(JPEG·PNG)만 저장**, 영상 기능 제외. `storage.image` 사용, 자동 삭제 없음. 매직 바이트·헤더 파싱·크기·픽셀 검증, SHA-256 저장 | 11장 |
| D5 | 탈퇴·관리자 | 회원 탈퇴 API와 시스템 관리자 기능 미구현 | — |
| D6 | 삭제 방식 | Soft Delete. `detail.deletion` / `metadata.deletion` / `files[].deletion`에 시간·삭제자·사유 저장. 일반 조회에서 제외. 서명 기록은 삭제 대신 무효화 기록(INVALIDATION) 생성 | 1.6, 7.5, 9.5, 10.2, 11.4 |
| D7 | JWT | Access Token 유효기간 **2시간**. 토큰 블랙리스트·강제 로그아웃·Refresh Token 제외 | 1.2 |
| D8 | 서명 무결성 | 서명 시점 기록 데이터의 SHA-256을 `confirmations[]`에 저장하고, 조회할 때마다 현재 데이터 해시와 비교. 서명 후 오류는 정정 기록으로 처리 | 4.4, 9.3, 12.1 |
| D9 | 무결성·동시성 | JSONB 참조·중복을 Service에서 검증. 검사와 변경을 같은 트랜잭션에서 행 잠금 후 수행 | 4장 |

---

## 17. 현재 스키마의 제약 사항

스키마를 변경하지 않기 때문에 아래 항목은 **DB가 보장하지 못하고 애플리케이션(Service 계층)에서만 보장**되거나 MVP에서 제한된다.

### 17.1 DB로 보장할 수 없어 Service에서 보장하는 사항

| 구분 | 현재 스키마로 보장할 수 없는 이유 | MVP 대응 | 남는 위험 |
|---|---|---|---|
| 참조 무결성 | `members[].uid`, `files[].storageId`, `correction.originalDataId`, 서명 `storageId`가 JSONB 안에 있어 FK를 걸 수 없음 | 쓰기 시 Service가 존재·소속·상태를 검증 ([4.2](#42-참조-유효성-검증-fk-대체)). 조회 시 깨진 참조는 제외하고 WARN 로그 | DB 직접 수정이나 계정 행 직접 삭제로 참조가 깨질 수 있음 |
| 중복 방지 | 참여자 uid, 같은 기록의 동일 이미지, 원본당 정정 1개를 UNIQUE로 강제할 수 없음 | 같은 행 잠금 아래에서 검사 후 변경 ([4.3](#43-중복-방지-unique-대체)) | 잠금 규칙을 지키지 않는 새 코드가 추가되면 중복 가능 → 코드 리뷰·동시성 테스트로 관리 |
| 서명 후 불변성 | 서명된 기록의 수정 금지, 확인·분석 이력의 추가 전용 규칙을 DB 제약·트리거로 강제할 수 없음 | API로는 변경 불가. 서명 해시로 **변조 탐지** (`integrity.status = MISMATCH`) | **변조 방지가 아닌 탐지**만 가능. DB 접근 권한자가 본문과 해시를 함께 바꾸면 탐지할 수 없음 |
| 동시성 | JSONB 배열 부분 갱신이 원자적이지 않고, 낙관적 잠금용 버전 컬럼이 없음 | 비관적 행 잠금 + read-modify-write, 잠금 순서 고정, 대기 3초 후 409 | 같은 기록에 요청이 몰리면 409가 늘어날 수 있음 (클라이언트 재시도 필요) |
| 시각 신뢰성 | `createdAt`, `confirmedAt` 등에 DB 기본값(`now()`)을 쓸 컬럼이 없음 | 애플리케이션 서버 시각을 UTC로 기록, 클라이언트 입력 불허 | 서버 시계가 틀리면 그대로 기록됨 |
| 파일 형식 | `storage`에 content_type 컬럼이 없음 | 형식·크기·해시를 `metadata`에 저장, 업로드 시 이미지만 허용 | DB 직접 INSERT된 비이미지 바이너리는 막을 수 없음 (연결된 기록이 없으면 다운로드 404) |

### 17.2 MVP에서 제한되는 사항

| 구분 | 제약 | MVP 대응 |
|---|---|---|
| 행 크기 | 확인·분석 이력이 쌓일수록 `metadata`가 커지고 수정 시 전체 행을 다시 씀 | 배열 크기 상한, AI result 256KB 제한 |
| 검색 성능 | JSONB 필드 인덱스가 없어 날짜·위치 조건이 순차 스캔됨 | 프로젝트 범위로 먼저 축소, 기간 366일 제한. 데이터 증가 시 성능 저하 예상 |
| 참여 프로젝트 조회 | `detail->'members'` 포함 검사가 전체 `project` 스캔 | MVP 규모에서는 허용 |
| 이미지 저장 | BYTEA는 조회 시 전체를 메모리에 적재 | 사진 10MB, 요청 100MB 상한. `verifyFiles=true`는 상세 조회에서만 허용 |
| 영상 | 영상 업로드·저장·재생 기능 없음 | MVP 범위 제외 (D4) |
| 파일 보관 | Soft Delete된 이미지도 `storage`에 남아 DB 용량이 계속 증가 | MVP 기간 자동 삭제 없음. 운영 시 보관 정책 검토 |
| 토큰 무효화 | 토큰 저장 테이블이 없음 | 로그아웃·비밀번호 변경 후에도 기존 토큰은 만료(2시간)까지 유효. 블랙리스트·강제 로그아웃 없음 |
| 삭제 복구 | 관리자 기능이 없어 Soft Delete된 데이터를 API로 복구할 수 없음 | 필요 시 DB에서 `deletion` 키를 제거해 복구 (운영 절차) |
| 기존 데이터 | API 외부에서 입력된 행(예: `type = 'IMAGE'`)은 정의한 구조와 다를 수 있음 | 읽기 시 누락 필드 기본값 처리, 알 수 없는 type은 그대로 반환하고 수정 시 400 |

---

## 18. 향후 개선 사항 (MVP 미적용)

> **아래 내용은 MVP에 적용하지 않는다.** `create_table.sql`과 DB는 변경하지 않으며, 운영 단계에서 17장의 제약을 해소하기 위해 검토할 항목으로만 남긴다. 적용이 확정되면 SQL 파일, `docs/database.md`, 본 문서를 함께 수정한다.

| # | 개선 항목 | 해소되는 제약 |
|---|---|---|
| F1 | `project_member` 테이블 (PK `(project_id, uid)`, FK) | 참여자 FK·중복, 참여 프로젝트 조회 성능 |
| F2 | `storage`에 `data_id` FK, `content_type`, `file_size`, `sha256`, `created_at` 추가 | 파일 참조 무결성, 다운로드 권한 조회 성능 |
| F3 | 오브젝트 스토리지(S3 등) 분리, 보관 기간 정책, 영상 지원 | 메모리 사용량, DB 용량, 영상 기능 부재 |
| F4 | `data_confirmation` 테이블 + UPDATE/DELETE 차단 트리거 | 서명 이력 불변성의 DB 레벨 보장 |
| F5 | `ai_analysis` 테이블 (UNIQUE `(data_id, version)`) | 행 크기, 분석 상태 조회 |
| F6 | `data`에 `captured_at`, `created_at`, `updated_at`, `deleted_at` 컬럼, `project`에 `deleted_at` | DB 시각 기록, Soft Delete 조회 성능 |
| F7 | JSONB 표현식·GIN 인덱스 (`metadata->>'capturedAt'`, `metadata jsonb_path_ops`, `detail jsonb_path_ops`) | 날짜·위치 검색, 파일 역검색, 참여자 조회 성능 |
| F8 | `data.correction_of` FK 컬럼 | 정정 체인 참조 무결성, 원본당 정정 1개 UNIQUE |
| F9 | `refresh_token` 테이블 | 로그아웃·비밀번호 변경 시 토큰 무효화 |
| F10 | `data.type` CHECK 제약 | 기록 유형 DB 레벨 검증 |
| F11 | AI 비동기 처리 (`PENDING` 상태 + AI 전용 서비스 토큰 콜백 API) | AI 서버 응답 지연 시 요청 스레드 점유 |

### 참고 SQL (예시, 미적용)

```sql
-- F1
CREATE TABLE project_member (
    project_id   BIGINT      NOT NULL REFERENCES project(project_id),
    uid          UUID        NOT NULL REFERENCES account(uid),
    project_role VARCHAR(20) NOT NULL CHECK (project_role IN ('MANAGER', 'MEMBER', 'VIEWER')),
    added_by     UUID        REFERENCES account(uid),
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, uid)
);

-- F7
CREATE INDEX idx_data_captured_at  ON data ((metadata->>'capturedAt'));
CREATE INDEX idx_data_metadata_gin ON data USING GIN (metadata jsonb_path_ops);
CREATE INDEX idx_project_detail_gin ON project USING GIN (detail jsonb_path_ops);

-- F8
ALTER TABLE data ADD COLUMN correction_of BIGINT REFERENCES data(data_id);
CREATE UNIQUE INDEX uq_data_correction_of ON data(correction_of) WHERE correction_of IS NOT NULL;
```

---

## 부록. 구현 시 필요한 의존성 (미추가)

| 의존성 | 용도 |
|---|---|
| `spring-boot-starter-security` | 인증·인가, PasswordEncoder |
| JWT 라이브러리 (예: `io.jsonwebtoken:jjwt`) | Access Token 발급·검증 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | Swagger UI (Spring Boot 4.x 호환 버전) |
| JSONB 매핑 | Hibernate 기본 `@JdbcTypeCode(SqlTypes.JSON)` — 추가 라이브러리 불필요 |
| AI 서버 호출 | Spring `RestClient` — 추가 라이브러리 불필요 |
| 파일 형식 판별 | 매직 바이트 직접 검사 — 추가 라이브러리 불필요 |
