# 백엔드 전용 프로젝트 개발 규칙

## 1. 프로젝트 범위
- 본 프로젝트는 REST API만 제공하는 백엔드 서버야.
- 프론트엔드(React, Vue, HTML, CSS, JavaScript 등)는 구현하지 마.
- Thymeleaf, JSP 등의 서버 사이드 화면도 구현하지 마.
- 모든 API는 JSON 형식으로 응답하되, 이미지 등 파일 다운로드 API는 적절한 바이너리 형식으로 응답해.
- API 문서 확인 및 테스트를 위해 Swagger UI만 사용해.

## 2. 아키텍처
기능별 패키지 구조를 적용하고 다음 계층을 분리해줘.

- Controller: HTTP 요청 및 응답, DTO 검증, Swagger 문서화
- Service: 비즈니스 로직, 권한 검사, 트랜잭션 처리
- ServiceImpl: Service 인터페이스를 사용하는 경우 구현체
- Repository: Spring Data JPA 기반 DB 접근
- Mapper: Entity와 DTO 간 데이터 변환
- Entity: JPA 엔티티 및 연관관계 매핑
- DTO: Request와 Response 분리

## 3. 인터페이스 규칙
- Repository는 JpaRepository 인터페이스를 사용해.
- Service는 구현체를 교체하거나 계약을 분리할 필요가 있으면 인터페이스와 구현체로 분리해.
- 모든 계층에 불필요하게 인터페이스를 만들지 마.
- Mapper는 반복되거나 복잡한 변환 로직이 있을 때 별도로 분리해.

## 4. Swagger 설정
- Springdoc OpenAPI를 사용해 Swagger UI를 구성해줘.
- API마다 @Tag, @Operation, @ApiResponse 등을 적절히 적용해줘.
- Request/Response DTO에 @Schema를 활용해 필드를 설명해줘.
- Swagger UI에서 요청 데이터를 입력하고 API를 직접 테스트할 수 있게 해줘.
- 인증이 필요한 API는 현재 인증 방식에 맞춰 Swagger 인증 기능을 설정해줘.

## 5. 구현 및 검증
- 프론트엔드 코드 없이 API만 구현해.
- API별 정상 및 예외 테스트를 작성해.
- DB 연결 및 CRUD 동작을 검증해.
- 빌드와 테스트를 실행하고 결과를 알려줘.
- 기존 프로젝트의 설정과 패키지 구조를 우선 확인해.

## 6. DB 및 예외 처리 규칙

- 기존 PostgreSQL 스키마의 PK, FK, UNIQUE, CHECK 제약조건을 유지해.
- Entity는 실제 DB 스키마와 일치하도록 매핑해.
- DB 구조 변경이 필요하다면 이유와 변경 SQL을 먼저 제안해.
- 요청 DTO는 Bean Validation으로 검증해.
- @RestControllerAdvice를 이용해 전역 예외 처리를 구현해.
- 오류 응답 형식을 통일하고 적절한 HTTP 상태 코드를 사용해.
- Entity를 API 응답으로 직접 반환하지 마.

## 7. 보안 및 트랜잭션 규칙

- 비밀번호는 PasswordEncoder를 이용해 안전하게 해시 처리해.
- 인증된 사용자에게 허용된 데이터만 조회·수정할 수 있도록 권한을 검증해.
- 필요한 Service 메서드에 @Transactional을 적용해.
- 조회 전용 트랜잭션에는 readOnly = true를 고려해.
- 비밀번호, 인증 토큰 등 민감정보를 로그나 API 응답에 노출하지 마.
- 파일 업로드 시 파일 크기와 실제 콘텐츠 유형을 검증해.

## 8. AI 개발 작업 규칙

- 코드를 작성하기 전에 기존 프로젝트 구조와 구현된 기능을 분석해.
- 요청한 기능에 필요한 파일만 생성하거나 수정해.
- 이미 구현된 기능을 중복으로 구현하지 마.
- 불필요한 라이브러리나 추상화를 추가하지 마.
- 기존 API의 동작을 임의로 변경하지 마.
- 요구사항이 불명확하면 가정과 확인이 필요한 사항을 구분해.
- 구현 후 변경한 파일과 주요 로직을 설명해.
- 빌드 및 테스트 결과를 보고하고, 실행하지 못한 테스트는 그 이유를 명시해.

## 9. API 명세서 관리 규칙

- 모든 API 명세는 `docs/api.md` 파일에서 관리해.
- 새로운 API를 구현하기 전에 `docs/api.md`에 API 명세를 먼저 작성하거나 기존 명세를 확인해.
- API 명세에는 다음 내용을 포함해.
  - API 이름 및 설명
  - HTTP Method 및 URL
  - 인증 필요 여부와 접근 가능한 사용자 역할
  - Path Variable 및 Query Parameter
  - Request Body 및 필드별 타입과 필수 여부
  - Response Body 및 예시 JSON
  - 정상 및 예외 HTTP 상태 코드
  - 주요 비즈니스 로직과 검증 조건
- API 구현은 `docs/api.md`의 명세를 기준으로 진행해.
- API 구현이나 수정으로 명세가 변경되면 `docs/api.md`도 함께 갱신해.
- Swagger(OpenAPI) 문서와 `docs/api.md`의 요청·응답 구조가 일치하도록 유지해.
- 기존 API의 명세를 임의로 변경하지 마.
- API 구현 후 `docs/api.md` 갱신 여부와 테스트 결과를 보고해.


## 10. 데이터베이스 설계 문서 관리

- DB 구조는 `docs/database.md`를 참고해.
- 실제 테이블 및 제약조건의 기준은 PostgreSQL SQL 스키마 파일이야.
- DB 관련 API나 Entity를 구현하기 전에 두 파일을 확인해.
- DB 구조 변경이 필요하면 먼저 변경안을 제안해.
- 스키마 변경이 확정되면 SQL 파일과 `docs/database.md`를 함께 수정해.