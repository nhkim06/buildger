# Database Design

## 1. 데이터베이스 개요

### 프로젝트

건설 현장 시공 이력 관리 시스템 (Buildger)

### 데이터베이스 환경

- DBMS: PostgreSQL
- ORM: Spring Data JPA / Hibernate
- Primary Key: UUID 또는 BIGINT
- 구조화된 추가 데이터: JSONB
- 이미지 바이너리: BYTEA

### 데이터베이스 목적

건설 현장의 시공 이력과 관련된 사용자, 프로젝트, 사진 및 영상 메타데이터, AI 분석 결과를 관리한다.

주요 데이터는 다음과 같다.

- 사용자 및 권한 정보
- 건설 프로젝트 정보
- 시공 기록 및 관련 메타데이터
- 이미지 원본 데이터
- AI 분석 결과와 담당자 확인 정보

실제 테이블 구조는 프로젝트의 SQL 스키마 파일을 기준으로 한다.

---

## 2. 테이블 관계

현재 DB는 4개의 테이블로 구성된다.

```text
ACCOUNT
  ├── PROJECT (created_by)
  └── DATA (uploaded_by)

PROJECT
  └── DATA (project_id)

STORAGE
  └── 현재 다른 테이블과 FK 연결 없음
```

### 관계 설명

| 부모 테이블 | 자식 테이블 | 관계 |
|---|---|---|
| ACCOUNT | PROJECT | 사용자 1명이 여러 프로젝트 생성 가능 |
| ACCOUNT | DATA | 사용자 1명이 여러 시공 기록 업로드 가능 |
| PROJECT | DATA | 프로젝트 1개에 여러 시공 기록 존재 가능 |
| DATA | STORAGE | 현재 FK 관계 없음 |

---

## 3. 테이블 상세 명세

### 3.1 ACCOUNT

사용자 계정과 인증 정보를 저장한다.

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| uid | UUID | PK, DEFAULT gen_random_uuid() | 사용자 고유 ID |
| login_id | VARCHAR(100) | NOT NULL, UNIQUE | 로그인 ID |
| password_hash | VARCHAR(255) | NOT NULL | 해시된 비밀번호 |
| role_level | SMALLINT | NOT NULL, CHECK | 사용자 권한 |
| organization | VARCHAR(255) | NULL 허용 | 소속기관 |
| name | VARCHAR(100) | NOT NULL | 사용자 이름 |
| phone | VARCHAR(20) | NULL 허용 | 전화번호 |

#### 사용자 권한

| 값 | 역할 |
|---|---|
| 1 | 현장직 |
| 2 | 시공사 |
| 3 | 시행사 |

role_level은 1, 2, 3만 허용한다.

#### 설계 규칙

- login_id는 중복될 수 없다.
- 비밀번호 원문을 저장하지 않는다.
- uid는 UUID를 사용한다.
- 역할별 API 접근 권한은 Spring Security에서 검증한다.

### 3.2 PROJECT

건설 프로젝트 정보를 저장한다.

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| project_id | BIGINT | PK, IDENTITY | 프로젝트 ID |
| pname | VARCHAR(255) | NOT NULL | 프로젝트 이름 |
| detail | JSONB | NOT NULL, DEFAULT '{}' | 프로젝트 상세 정보 |
| created_by | UUID | FK, NULL 허용 | 프로젝트 생성자 |

#### 관계

`created_by → ACCOUNT.uid`

#### 설계 규칙

- 프로젝트 ID는 DB에서 자동 생성한다.
- 프로젝트 생성자는 ACCOUNT를 참조한다.
- detail은 JSONB로 프로젝트의 추가 정보를 저장한다.
- JSONB 내부 필드 구조는 현재 DB 제약조건으로 강제되지 않는다.

#### 보완 필요 사항

현재 created_by만으로는 여러 사용자가 하나의 프로젝트에 참여하는 구조를 명확하게 표현할 수 없다.

프로젝트 참여자 및 접근 권한 관리 방식은 별도 설계가 필요하다.

### 3.3 DATA

프로젝트별 시공 기록 및 분석 관련 메타데이터를 저장한다.

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| data_id | BIGINT | PK, IDENTITY | 시공 기록 ID |
| project_id | BIGINT | NOT NULL, FK | 소속 프로젝트 |
| uploaded_by | UUID | FK, NULL 허용 | 업로드 사용자 |
| type | VARCHAR(50) | NOT NULL | 데이터 유형 |
| metadata | JSONB | NOT NULL, DEFAULT '{}' | 시공 기록 메타데이터 |
| url | TEXT | NULL 허용 | 파일 또는 관련 자료 URL |

#### 관계

- `project_id → PROJECT.project_id`
- `uploaded_by → ACCOUNT.uid`

#### 데이터 저장 목적

metadata에는 향후 다음 정보의 저장을 고려한다.

- 시공 위치
- 촬영 날짜
- 시공 공정 정보
- AI 분석 결과
- 담당자 확인 및 서명 정보

단, 위 항목은 현재 확정된 JSONB 스키마가 아니다.

구체적인 필드명, 자료형, 필수 여부는 API 설계 시 별도로 확정해야 한다.

#### 설계 규칙

- 모든 DATA는 프로젝트에 소속되어야 한다.
- type은 필수값이지만 현재 허용 값에 대한 CHECK 제약조건은 없다.
- uploaded_by는 현재 NULL을 허용한다.
- AI 분석 결과와 원본 데이터의 관계를 명확하게 설계할 필요가 있다.

### 3.4 STORAGE

이미지 바이너리 데이터를 저장한다.

| 컬럼 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| storage_id | BIGINT | PK, IDENTITY | 저장 파일 ID |
| name | VARCHAR(255) | NULL 허용 | 이미지 이름 |
| image | BYTEA | NOT NULL | 이미지 바이너리 |

#### 설계 규칙

- 이미지 바이너리는 PostgreSQL BYTEA 타입으로 저장한다.
- STORAGE와 DATA 사이에 현재 FK가 없다.
- 이미지 조회 API는 적절한 Content-Type으로 바이너리를 반환해야 한다.
- 영상 파일의 저장 방식은 아직 별도로 정의되지 않았다.

---

## 4. 인덱스

현재 SQL 스키마에 정의된 인덱스는 다음과 같다.

| 인덱스 이름 | 대상 컬럼 | 목적 |
|---|---|---|
| idx_data_project_id | DATA.project_id | 프로젝트별 기록 조회 |
| idx_data_uploaded_by | DATA.uploaded_by | 사용자별 업로드 기록 조회 |
| idx_project_created_by | PROJECT.created_by | 생성자별 프로젝트 조회 |

ACCOUNT.login_id에는 UNIQUE 제약조건이 있으므로 PostgreSQL에서 해당 제약조건을 지원하는 고유 인덱스가 생성된다.

---

## 5. DB 제약조건 및 데이터 관리 원칙

### 무결성

- PK, FK, UNIQUE, NOT NULL, CHECK 제약조건을 준수한다.
- 존재하지 않는 프로젝트에 시공 기록을 등록할 수 없다.
- 존재하지 않는 사용자의 ID를 FK로 저장할 수 없다.
- FK의 NULL 허용 여부는 실제 스키마를 따른다.

### 데이터 변경

- DB 구조 변경이 필요한 경우 변경 이유를 먼저 검토한다.
- 새로운 컬럼이나 테이블을 임의로 추가하지 않는다.
- 기존 컬럼 타입과 제약조건을 임의로 변경하지 않는다.
- 삭제 시 FK 관계와 연관 데이터 처리 방식을 확인한다.
- 테이블 삭제 및 데이터 초기화는 명시적인 요청이 있을 때만 수행한다.

### JPA 매핑

- UUID는 Java UUID 타입으로 매핑한다.
- BIGINT는 Java Long 타입으로 매핑한다.
- PostgreSQL JSONB에 적합한 매핑 방식을 사용한다.
- BYTEA는 byte[] 등 적절한 타입으로 매핑한다.
- IDENTITY 컬럼은 DB 자동 생성 전략을 따른다.
- Entity와 실제 DB 스키마가 일치하도록 유지한다.

---

## 6. 추가 설계 검토 사항

### 6.1 DATA와 STORAGE 연결

현재 DATA와 STORAGE 사이에 FK가 없다.

사진 한 장과 시공 기록 하나의 관계를 어떻게 정의할지 결정해야 한다.

가능한 방식:
- DATA에 storage_id FK 추가
- 별도 연결 테이블 사용
- URL 기반 파일 저장 방식으로 통일

파일 하나와 시공 기록 하나의 관계가 1:1인지 1:N인지 먼저 결정한다.

### 6.2 프로젝트 참여자 관리

프로젝트 생성자 외에도 여러 사용자가 프로젝트에 참여할 수 있다.

프로젝트 참여자와 역할을 관리하는 별도 테이블이 필요한지 검토한다.

### 6.3 담당자 서명 및 확인 이력

현재 서명 정보는 DATA.metadata에 저장하는 방향으로 설계되어 있다.

다음 사항을 검토해야 한다.

- 확인 담당자
- 확인 시간
- 서명 데이터
- 승인 상태
- 승인 및 변경 이력

증빙자료의 신뢰성을 위해 별도 이력 테이블이 필요한지 검토한다.

### 6.4 AI 분석 결과 관리

AI가 분석한 시공 정보를 저장하는 방식을 결정해야 한다.

- 원본 시공 기록과 분석 결과의 관계
- 분석 상태 및 실패 상태
- 분석 결과의 버전
- 분석 일시
- 날짜 및 위치 기반 검색 구조

### 6.5 증거 데이터 무결성

하자 분쟁 시 증빙자료로 활용할 수 있도록 다음 사항을 검토한다.

- 촬영 일시와 업로드 일시 구분
- 원본 파일의 해시값
- 수정 및 삭제 이력
- 원본 데이터 보존 정책

---

## 7. Claude Code 작업 규칙

- DB 관련 코드를 구현하기 전에 본 문서와 실제 SQL 스키마를 확인한다.
- 실제 SQL 스키마가 현재 DB 구조의 기준이다.
- 본 문서의 추가 설계 검토 사항은 구현이 확정된 기능이 아니다.
- 변경이 필요한 경우 변경 목적, 영향받는 테이블, 마이그레이션 SQL을 먼저 제안한다.
- JPA Entity를 작성할 때 컬럼명, 타입, PK/FK, 제약조건을 확인한다.
- API 구현 시 DB 구조와 일치하는지 검증한다.
- DB 스키마가 변경되면 본 문서도 함께 갱신한다.