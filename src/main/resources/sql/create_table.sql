-- ============================================
-- 기존 테이블 삭제
-- ============================================

DROP TABLE IF EXISTS data;
DROP TABLE IF EXISTS storage;
DROP TABLE IF EXISTS project;
DROP TABLE IF EXISTS account;

-- ============================================
-- 건설 현장 시공 이력 관리 시스템
-- PostgreSQL Database Schema
-- ============================================


-- ============================================
-- 1. ACCOUNT
-- 사용자 정보 및 로그인
-- ============================================

CREATE TABLE IF NOT EXISTS account (
    -- 사용자 고유 ID (UUID 자동 생성)
                                       uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 로그인 아이디 (중복 불가)
                                       login_id VARCHAR(100) NOT NULL UNIQUE,

    -- 암호화된 비밀번호
                                       password_hash VARCHAR(255) NOT NULL,

    -- 사용자 권한
    -- 1: 현장직 / 2: 시공사 / 3: 시행사
                                       role_level SMALLINT NOT NULL
                                           CHECK (role_level IN (1, 2, 3)),

    -- 소속기관
                                       organization VARCHAR(255),

    -- 사용자 이름
                                       name VARCHAR(100) NOT NULL,

    -- 전화번호
                                       phone VARCHAR(20)
);


-- ============================================
-- 2. PROJECT
-- 프로젝트 정보
-- ============================================

CREATE TABLE IF NOT EXISTS project (
    -- 프로젝트 고유 ID (자동 증가)
                                       project_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 프로젝트 이름
                                       pname VARCHAR(255) NOT NULL,

    -- 프로젝트 정보 및 소속 계정 정보
                                       detail JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 프로젝트 생성자
                                       created_by UUID,

                                       CONSTRAINT fk_project_account
                                           FOREIGN KEY (created_by)
                                               REFERENCES account(uid)
);


-- ============================================
-- 3. DATA
-- 시공 기록 및 AI 분석 결과
-- ============================================

CREATE TABLE IF NOT EXISTS data (
    -- 시공 기록 고유 ID (자동 증가)
                                    data_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 소속 프로젝트 ID
                                    project_id BIGINT NOT NULL,

    -- 업로드한 사용자 ID
                                    uploaded_by UUID,

    -- 데이터 유형 (예: IMAGE)
                                    type VARCHAR(50) NOT NULL,

    -- 시공 위치, AI 분석, 사진 및 서명 정보
                                    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 파일 또는 관련 자료 URL (선택)
                                    url TEXT,

                                    CONSTRAINT fk_data_project
                                        FOREIGN KEY (project_id)
                                            REFERENCES project(project_id),

                                    CONSTRAINT fk_data_account
                                        FOREIGN KEY (uploaded_by)
                                            REFERENCES account(uid)
);


-- ============================================
-- 4. STORAGE
-- 이미지 바이너리 저장
-- ============================================

CREATE TABLE IF NOT EXISTS storage (
    -- 이미지 고유 ID (자동 증가)
                                       storage_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 이미지 이름
                                       name VARCHAR(255),

    -- 실제 이미지 바이너리
                                       image BYTEA NOT NULL
);


-- ============================================
-- 5. INDEX
-- 조회 성능 개선
-- ============================================

-- 프로젝트별 시공 기록 조회
CREATE INDEX IF NOT EXISTS idx_data_project_id
    ON data(project_id);

-- 사용자별 업로드 기록 조회
CREATE INDEX IF NOT EXISTS idx_data_uploaded_by
    ON data(uploaded_by);

-- 프로젝트 생성자별 조회
CREATE INDEX IF NOT EXISTS idx_project_created_by
    ON project(created_by);


-- ============================================
-- 6. 테이블 생성 확인
-- ============================================

SELECT
    table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
                     'account',
                     'project',
                     'data',
                     'storage'
    )
ORDER BY table_name;
