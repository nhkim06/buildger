-- ============================================
-- 테스트용 Dummy Data (PostgreSQL)
-- 기준: create_table.sql, docs/api.md 3장(JSONB 구조)·4.4(서명 스냅샷 해시)
--
-- * 스키마(테이블·제약조건)는 변경하지 않는다. 임시 함수(pg_temp)는 세션 종료 시 사라진다.
-- * 전체가 하나의 트랜잭션이며, dummy_ 계정이 이미 있으면 아무것도 넣지 않고 중단한다.
-- * 서명된 기록의 recordSnapshotSha256, 파일 sha256은 DB에서 계산하므로
--   API 조회 시 integrity.status = VERIFIED, verifyFiles=true 시 fileStatus = VERIFIED가 된다.
--
-- [테스트 계정] 비밀번호는 모두  Test1234!
-- | loginId          | role_level      | 이름   | 프로젝트 역할                                   |
-- |------------------|-----------------|--------|-------------------------------------------------|
-- | dummy_developer  | 3 (DEVELOPER)   | 김시행 | P1 OWNER, P2 VIEWER, P3(삭제됨) OWNER           |
-- | dummy_contractor | 2 (CONTRACTOR)  | 이시공 | P1 MANAGER, P2 OWNER                            |
-- | dummy_worker1    | 1 (FIELD_WORKER)| 박현장 | P1 MEMBER, P2 MEMBER                            |
-- | dummy_worker2    | 1 (FIELD_WORKER)| 최현장 | P1 MEMBER                                       |
-- | dummy_viewer     | 1 (FIELD_WORKER)| 정열람 | P1 VIEWER                                       |
-- | dummy_outsider   | 2 (CONTRACTOR)  | 한외부 | 참여 프로젝트 없음 (403 테스트용)               |
--
-- [시공 기록] (P1 = 강남 업무시설 신축공사)
-- | 기록 | 프로젝트 | type         | 작성자  | 상태                                                    |
-- |------|----------|--------------|---------|---------------------------------------------------------|
-- | R1   | P1       | PHOTO        | worker1 | 사진1 + AI 분석 성공 + developer 승인 서명 → R4로 정정됨 (SUPERSEDED) |
-- | R2   | P1       | INSPECTION   | worker2 | 사진1 + contractor 반려 서명 → R5로 무효화됨 (INVALIDATED) |
-- | R3   | P1       | NOTE         | worker1 | 서명 전 (수정·삭제·서명 테스트용)                        |
-- | R4   | P1       | PHOTO        | worker1 | R1의 정정 기록(AMENDMENT), 사진1, 서명 전               |
-- | R5   | P1       | INVALIDATION | contractor | R2의 무효화 기록                                     |
-- | R6   | P1       | NOTE         | worker2 | Soft Delete 됨                                          |
-- | R7   | P1       | PHOTO        | worker1 | 사진1 + AI 분석 실패 이력, 서명 전 (서명·AI 재요청 테스트용) |
-- | R8   | P2       | NOTE         | worker1 | 서명 전                                                 |
-- ============================================

BEGIN;

-- 문자열을 JSON 문자열(또는 null)로 변환
CREATE OR REPLACE FUNCTION pg_temp.js(t text) RETURNS text
    LANGUAGE sql IMMUTABLE AS
$$ SELECT CASE WHEN t IS NULL THEN 'null' ELSE to_json(t)::text END $$;

-- RecordSnapshotHasher(스냅샷 v1)와 같은 정규화 규칙으로 현재 data 행의 해시를 계산
-- 키 사전순, 공백 없음, 위·경도는 문자열, 삭제되지 않은 파일만 storageId 오름차순
CREATE OR REPLACE FUNCTION pg_temp.snapshot_hash(p_data_id bigint) RETURNS text
    LANGUAGE plpgsql AS
$$
DECLARE
    d     data%ROWTYPE;
    m     jsonb;
    l     jsonb;
    c     jsonb;
    loc   text;
    files text;
    corr  text;
    canon text;
BEGIN
    SELECT * INTO d FROM data WHERE data_id = p_data_id;
    m := d.metadata;
    l := m -> 'location';
    c := m -> 'correction';

    IF l IS NULL OR jsonb_typeof(l) = 'null' THEN
        loc := 'null';
    ELSE
        loc := '{"address":'    || pg_temp.js(l ->> 'address')
            || ',"building":'   || pg_temp.js(l ->> 'building')
            || ',"floor":'      || pg_temp.js(l ->> 'floor')
            || ',"latitude":'   || pg_temp.js(l ->> 'latitude')
            || ',"longitude":'  || pg_temp.js(l ->> 'longitude')
            || ',"site":'       || pg_temp.js(l ->> 'site')
            || ',"zone":'       || pg_temp.js(l ->> 'zone')
            || '}';
    END IF;

    SELECT '[' || coalesce(string_agg(
                   '{"sha256":' || pg_temp.js(f ->> 'sha256') || ',"storageId":' || (f ->> 'storageId') || '}',
                   ',' ORDER BY (f ->> 'storageId')::bigint), '') || ']'
    INTO files
    FROM jsonb_array_elements(coalesce(m -> 'files', '[]'::jsonb)) AS f
    WHERE f -> 'deletion' IS NULL;

    IF c IS NULL OR jsonb_typeof(c) = 'null' THEN
        corr := 'null';
    ELSE
        corr := '{"correctedAt":'     || pg_temp.js(c ->> 'correctedAt')
             || ',"correctedBy":'     || pg_temp.js(c ->> 'correctedBy')
             || ',"kind":'            || pg_temp.js(c ->> 'kind')
             || ',"originalDataId":'  || coalesce(c ->> 'originalDataId', 'null')
             || ',"reason":'          || pg_temp.js(c ->> 'reason')
             || ',"rootDataId":'      || coalesce(c ->> 'rootDataId', 'null')
             || '}';
    END IF;

    canon := '{"capturedAt":'       || pg_temp.js(m ->> 'capturedAt')
          || ',"correction":'       || corr
          || ',"createdAt":'        || pg_temp.js(m ->> 'createdAt')
          || ',"dataId":'           || d.data_id
          || ',"description":'      || pg_temp.js(m ->> 'description')
          || ',"files":'            || files
          || ',"location":'         || loc
          || ',"process":'          || pg_temp.js(m ->> 'process')
          || ',"projectId":'        || d.project_id
          || ',"snapshotVersion":1'
          || ',"title":'            || pg_temp.js(m ->> 'title')
          || ',"type":'             || pg_temp.js(d.type)
          || ',"uploadedBy":'       || pg_temp.js(d.uploaded_by::text)
          || ',"url":'              || pg_temp.js(d.url)
          || '}';

    RETURN encode(sha256(convert_to(canon, 'UTF8')), 'hex');
END
$$;

-- storage 행으로 metadata.files[] 항목 생성 (sha256·size는 실제 바이너리에서 계산)
CREATE OR REPLACE FUNCTION pg_temp.file_entry(p_storage_id bigint, p_width int, p_height int,
                                   p_uploaded_by uuid, p_uploaded_at text) RETURNS jsonb
    LANGUAGE sql AS
$$
SELECT jsonb_build_object(
               'storageId', s.storage_id,
               'originalName', s.name,
               'contentType', 'image/png',
               'size', octet_length(s.image),
               'sha256', encode(sha256(s.image), 'hex'),
               'width', p_width,
               'height', p_height,
               'uploadedBy', p_uploaded_by::text,
               'uploadedAt', p_uploaded_at)
FROM storage s
WHERE s.storage_id = p_storage_id
$$;

-- storage 행으로 confirmations[].signature 생성
CREATE OR REPLACE FUNCTION pg_temp.signature_entry(p_storage_id bigint) RETURNS jsonb
    LANGUAGE sql AS
$$
SELECT jsonb_build_object(
               'storageId', s.storage_id,
               'contentType', 'image/png',
               'size', octet_length(s.image),
               'sha256', encode(sha256(s.image), 'hex'))
FROM storage s
WHERE s.storage_id = p_storage_id
$$;

DO
$$
DECLARE
    -- 계정 uid (고정값: JSONB에서 참조하기 위함)
    u_dev   CONSTANT uuid := 'd0000000-0000-4000-8000-000000000001';
    u_con   CONSTANT uuid := 'd0000000-0000-4000-8000-000000000002';
    u_w1    CONSTANT uuid := 'd0000000-0000-4000-8000-000000000003';
    u_w2    CONSTANT uuid := 'd0000000-0000-4000-8000-000000000004';
    u_view  CONSTANT uuid := 'd0000000-0000-4000-8000-000000000005';
    u_out   CONSTANT uuid := 'd0000000-0000-4000-8000-000000000006';
    -- BCrypt("Test1234!")
    pw      CONSTANT text := '$2a$10$tDKnaQgDp4ewxivNQiL65e7/4yDHJX9wpUqzffiVn66vSUKqmwkJS';

    p1 bigint; p2 bigint; p3 bigint;
    s_photo1 bigint; s_photo2 bigint; s_photo3 bigint; s_photo4 bigint; s_sig1 bigint; s_sig2 bigint;
    r1 bigint; r2 bigint; r3 bigint; r4 bigint; r5 bigint; r6 bigint; r7 bigint; r8 bigint;

    loc_slab   jsonb := jsonb_build_object('site', 'A동', 'building', '본관', 'floor', '3F',
                                           'zone', 'X1-X3 / Y1-Y4', 'address', '서울특별시 강남구 테헤란로 123',
                                           'latitude', 37.501274, 'longitude', 127.039585);
    loc_b2     jsonb := jsonb_build_object('site', 'A동', 'building', '본관', 'floor', 'B2',
                                           'zone', '기계실', 'address', '서울특별시 강남구 테헤란로 123',
                                           'latitude', 37.501274, 'longitude', 127.039585);
    loc_4f     jsonb := jsonb_build_object('site', 'A동', 'building', '본관', 'floor', '4F',
                                           'zone', 'X1-X3 / Y1-Y4', 'address', '서울특별시 강남구 테헤란로 123',
                                           'latitude', 37.501274, 'longitude', 127.039585);
    loc_b_wall jsonb := jsonb_build_object('site', 'B동', 'building', '별관', 'floor', '1F',
                                           'zone', '외벽 남측', 'address', '서울특별시 강남구 테헤란로 125',
                                           'latitude', 37.501512, 'longitude', 127.039871);
BEGIN
    IF EXISTS (SELECT 1 FROM account WHERE login_id LIKE 'dummy\_%') THEN
        RAISE EXCEPTION 'dummy 데이터가 이미 존재합니다. 정리 SQL(파일 하단) 실행 후 다시 시도하세요.';
    END IF;

    -- ========== 1. ACCOUNT ==========
    INSERT INTO account (uid, login_id, password_hash, role_level, organization, name, phone) VALUES
        (u_dev,  'dummy_developer',  pw, 3, '한빛개발',     '김시행', '010-1000-0001'),
        (u_con,  'dummy_contractor', pw, 2, '대한건설',     '이시공', '010-1000-0002'),
        (u_w1,   'dummy_worker1',    pw, 1, '대한건설',     '박현장', '010-1000-0003'),
        (u_w2,   'dummy_worker2',    pw, 1, '대한건설',     '최현장', '010-1000-0004'),
        (u_view, 'dummy_viewer',     pw, 1, '한빛개발',     '정열람', '010-1000-0005'),
        (u_out,  'dummy_outsider',   pw, 2, '타사건설',     '한외부', NULL);

    -- ========== 2. PROJECT ==========
    INSERT INTO project (pname, detail, created_by) VALUES
        ('강남 업무시설 신축공사', jsonb_build_object(
            'schemaVersion', 1,
            'description', '지하 3층, 지상 15층 업무시설',
            'address', '서울특별시 강남구 테헤란로 123',
            'startDate', '2026-03-01',
            'endDate', '2027-08-31',
            'status', 'IN_PROGRESS',
            'createdAt', '2026-11-02T00:00:00Z',
            'updatedAt', '2026-11-02T00:10:00Z',
            'updatedBy', u_dev::text,
            'members', jsonb_build_array(
                jsonb_build_object('uid', u_con::text,  'projectRole', 'MANAGER', 'addedBy', u_dev::text,
                                   'addedAt', '2026-11-02T00:10:00Z', 'updatedAt', '2026-11-02T00:10:00Z'),
                jsonb_build_object('uid', u_w1::text,   'projectRole', 'MEMBER',  'addedBy', u_dev::text,
                                   'addedAt', '2026-11-02T00:10:00Z', 'updatedAt', '2026-11-02T00:10:00Z'),
                jsonb_build_object('uid', u_w2::text,   'projectRole', 'MEMBER',  'addedBy', u_dev::text,
                                   'addedAt', '2026-11-02T00:10:00Z', 'updatedAt', '2026-11-02T00:10:00Z'),
                jsonb_build_object('uid', u_view::text, 'projectRole', 'VIEWER',  'addedBy', u_dev::text,
                                   'addedAt', '2026-11-02T00:10:00Z', 'updatedAt', '2026-11-02T00:10:00Z'))),
         u_dev)
    RETURNING project_id INTO p1;

    INSERT INTO project (pname, detail, created_by) VALUES
        ('판교 물류센터 증축공사', jsonb_build_object(
            'schemaVersion', 1,
            'description', '지상 4층 물류센터 증축',
            'address', '경기도 성남시 분당구 판교로 45',
            'startDate', '2027-01-15',
            'endDate', '2027-12-20',
            'status', 'PLANNED',
            'createdAt', '2026-11-05T01:00:00Z',
            'updatedAt', '2026-11-05T01:05:00Z',
            'updatedBy', u_con::text,
            'members', jsonb_build_array(
                jsonb_build_object('uid', u_w1::text,  'projectRole', 'MEMBER', 'addedBy', u_con::text,
                                   'addedAt', '2026-11-05T01:05:00Z', 'updatedAt', '2026-11-05T01:05:00Z'),
                jsonb_build_object('uid', u_dev::text, 'projectRole', 'VIEWER', 'addedBy', u_con::text,
                                   'addedAt', '2026-11-05T01:05:00Z', 'updatedAt', '2026-11-05T01:05:00Z'))),
         u_con)
    RETURNING project_id INTO p2;

    INSERT INTO project (pname, detail, created_by) VALUES
        ('인천 주상복합 리모델링', jsonb_build_object(
            'schemaVersion', 1,
            'description', '사업 취소로 삭제된 프로젝트 (Soft Delete 테스트용)',
            'address', '인천광역시 연수구 컨벤시아대로 10',
            'status', 'SUSPENDED',
            'createdAt', '2026-10-01T00:00:00Z',
            'updatedAt', '2026-10-01T00:00:00Z',
            'updatedBy', u_dev::text,
            'members', '[]'::jsonb,
            'deletion', jsonb_build_object('deletedAt', '2026-11-01T00:00:00Z',
                                           'deletedBy', u_dev::text, 'reason', '사업 취소')),
         u_dev)
    RETURNING project_id INTO p3;

    -- ========== 3. STORAGE (실제 PNG 바이너리) ==========
    INSERT INTO storage (name, image) VALUES ('slab_rebar_3f.png', decode('iVBORw0KGgoAAAANSUhEUgAAAEAAAAAwCAIAAAAuKetIAAAArklEQVR4Xu2TMQ4DQQgD/bS80nWemlOQrqByh42wtlnGzQgB8lvv88/7bc+TPg9i1ZNyg0A1coM+SAvEZXlSbrgBsepJeQLDlCcwTLlBoBq5QR+kBeKyPCk33IBY9aQ8gWHKExim3CBQjdygD9ICcVmelBtuQKx6Up7AMOUJDFNuEKhGbtAHaYG4LE/KDTcgVj0pT2CY8gSGKTcIVCM36IO0QFyWJ+WGGxCrnvR5P1lyFaAdQPvYAAAAAElFTkSuQmCC', 'base64')) RETURNING storage_id INTO s_photo1;
    INSERT INTO storage (name, image) VALUES ('waterproof_b2.png', decode('iVBORw0KGgoAAAANSUhEUgAAAEAAAAAwCAIAAAAuKetIAAAAkUlEQVR4Xu2SwQmAAAwDM5pzOISTOLRgQaSv/tKDhL5y+RxU53V/d7z5N/up5tOdlC9QmBv1ghb+C82nO2kE3DQCbhoBN1VhbtQLWvgvNJ/upBFw0wi4aQTcVIW5US9o4b/QfLqTRsBNI+CmEXBTFeZGvaCF/0Lz6U4aATeNgJtGwE1VmBv1ghb+C82nOyle4AGTpHckF8crWwAAAABJRU5ErkJggg==', 'base64')) RETURNING storage_id INTO s_photo2;
    INSERT INTO storage (name, image) VALUES ('slab_rebar_4f.png', decode('iVBORw0KGgoAAAANSUhEUgAAAEAAAAAwCAIAAAAuKetIAAAAkUlEQVR4Xu2SsQnAQAwDNVomyiSZJFMG4uZx5U4+kHClU3Ngvc993vWnlZup5tOdlC9QjBv1ghb+C82nO2kE3DQCbhoBN1UxbtQLWvgvNJ/upBFw0wi4aQTcVMW4US9o4b/QfLqTRsBNI+CmEXBTFeNGvaCF/0Lz6U4aATeNgJtGwE1VjBv1ghb+C82nOyle4APDKODCObm1ngAAAABJRU5ErkJggg==', 'base64')) RETURNING storage_id INTO s_photo3;
    INSERT INTO storage (name, image) VALUES ('curtain_wall_south.png', decode('iVBORw0KGgoAAAANSUhEUgAAAEAAAAAwCAIAAAAuKetIAAAAmElEQVR4Xu2SsQkDAQwDNdoPkyEySeZ9iDsb1/KBhCudmgPr+/u0e/6Z/U2qWW3Tm5QvUIAb9YIW/gvNapvepBFw0wi4aQTcVAW4US9o4b/QrLbpTRoBN42Am0bATVWAG/WCFv4LzWqb3qQRcNMIuGkE3FQFuFEvaOG/0Ky26U0aATeNgJtGwE1VgBv1ghb+C81qm96keIEXqT1ew+umSlgAAAAASUVORK5CYII=', 'base64')) RETURNING storage_id INTO s_photo4;
    INSERT INTO storage (name, image) VALUES ('signature.png', decode('iVBORw0KGgoAAAANSUhEUgAAAGAAAAAgCAIAAABiouoDAAABC0lEQVR4Xu3VUQ6DIBCEYY/G/S9FU2nMdKosrAuVZL5HWQH/F7csVRs/kG8KZFAggwIZFMigQAYFMiiQQYE+Ukr8aKdA7zQHXvtXoKvbTFZPU8wLhLc58NAs7XcYGwjvgWiJXxus6+j4QHg84rldfTUcXKf1xJhAeDDiuTO98z7uU/yB8EjEc21uvl4BV/Ns3hcID0M85xK7WwF3dG5rB8IzEM9FCNw/aqvzQLg74rkxbh4H93XugOxAvDaF7wK+t+rOAz1E19dCnKb5Ro8OlNs+u2XG7emBiqvvhzK8FGWNQPmnEZQZlaZYJlC++LfyULSVAhXT0hTrBcp7I340zJKBZlIggwIZFMjwAvMVRqG5GRgpAAAAAElFTkSuQmCC', 'base64')) RETURNING storage_id INTO s_sig1;
    INSERT INTO storage (name, image) VALUES ('signature.png', decode('iVBORw0KGgoAAAANSUhEUgAAAGAAAAAgCAIAAABiouoDAAAA+0lEQVR4Xu3VXRKDIAxGUZfm/jdFp8Y66ddMEiCgTHMekd/74laSasOB9C0DGTKQIQMZMpAhAxkykCEDGTLQad93HDpkoHeaC37780B6GnJPIP1OE3jSkBmB+G0ATp2i6gJDAvEbiGAOrh+m4dCYQPxgES748MwJ0XxQSyB+mAgXqJoXOnXu7wrEzxDhgnqxu136t5UD8X1FuCBC7P5Ru7kC4eeR+s/t34GTA92u7YWsTN1CxUMDlfrX1s53em4g4nkzK6NNa/P0QEV9v/IpygKBCIRgZUalIcsEKj//VoKToq0UiExLQ9YLVI5GODTMkoFmykCGDGTIQIYXs/tCJ1A3UhcAAAAASUVORK5CYII=', 'base64')) RETURNING storage_id INTO s_sig2;

    -- ========== 4. DATA ==========

    -- R1: 사진 + AI 분석 성공 + 승인 서명 (이후 R4로 정정)
    INSERT INTO data (project_id, uploaded_by, type, metadata, url) VALUES
        (p1, u_w1, 'PHOTO', jsonb_build_object(
            'schemaVersion', 1,
            'title', '3층 슬래브 철근 배근',
            'description', 'D13 @200 배근 완료',
            'process', '철근콘크리트공사',
            'capturedAt', '2026-11-10T01:20:00Z',
            'location', loc_slab,
            'createdAt', '2026-11-10T02:00:00Z',
            'updatedAt', '2026-11-10T02:01:00Z',
            'updatedBy', u_w1::text,
            'files', jsonb_build_array(pg_temp.file_entry(s_photo1, 64, 48, u_w1, '2026-11-10T02:01:00Z')),
            'confirmations', '[]'::jsonb,
            'aiAnalyses', jsonb_build_array(jsonb_build_object(
                'analysisId', 'a0000000-0000-4000-8000-000000000001',
                'version', 1,
                'status', 'SUCCESS',
                'model', 'rebar-detector-v2',
                'targetStorageIds', jsonb_build_array(s_photo1),
                'summary', '철근 간격 평균 198mm, 기준 충족',
                'result', jsonb_build_object('rebarSpacingMm', 198, 'defects', '[]'::jsonb),
                'requestedBy', u_w1::text,
                'requestedAt', '2026-11-10T02:05:00Z',
                'completedAt', '2026-11-10T02:05:20Z'))),
         NULL)
    RETURNING data_id INTO r1;

    UPDATE data SET metadata = jsonb_set(metadata, '{confirmations}', jsonb_build_array(jsonb_build_object(
            'confirmationId', 'c0000000-0000-4000-8000-000000000001',
            'confirmerUid', u_dev::text,
            'confirmerName', '김시행',
            'confirmerOrganization', '한빛개발',
            'confirmerRoleLevel', 3,
            'status', 'APPROVED',
            'comment', '배근 간격 확인 완료',
            'signature', pg_temp.signature_entry(s_sig1),
            'snapshotVersion', 1,
            'recordSnapshotSha256', pg_temp.snapshot_hash(r1),
            'confirmedAt', '2026-11-11T00:30:00Z')))
    WHERE data_id = r1;

    -- R2: 검측 + 반려 서명 (이후 R5로 무효화)
    INSERT INTO data (project_id, uploaded_by, type, metadata, url) VALUES
        (p1, u_w2, 'INSPECTION', jsonb_build_object(
            'schemaVersion', 1,
            'title', '지하 2층 기계실 방수 검측',
            'description', '우레탄 방수 2차 도포 후 검측',
            'process', '방수공사',
            'capturedAt', '2026-11-11T05:00:00Z',
            'location', loc_b2,
            'createdAt', '2026-11-11T05:30:00Z',
            'updatedAt', '2026-11-11T05:31:00Z',
            'updatedBy', u_w2::text,
            'files', jsonb_build_array(pg_temp.file_entry(s_photo2, 64, 48, u_w2, '2026-11-11T05:31:00Z')),
            'confirmations', '[]'::jsonb,
            'aiAnalyses', '[]'::jsonb),
         'https://example.com/inspection/b2-waterproof-checklist.pdf')
    RETURNING data_id INTO r2;

    UPDATE data SET metadata = jsonb_set(metadata, '{confirmations}', jsonb_build_array(jsonb_build_object(
            'confirmationId', 'c0000000-0000-4000-8000-000000000002',
            'confirmerUid', u_con::text,
            'confirmerName', '이시공',
            'confirmerOrganization', '대한건설',
            'confirmerRoleLevel', 2,
            'status', 'REJECTED',
            'comment', '배수구 주변 방수층 들뜸, 재시공 필요',
            'signature', pg_temp.signature_entry(s_sig2),
            'snapshotVersion', 1,
            'recordSnapshotSha256', pg_temp.snapshot_hash(r2),
            'confirmedAt', '2026-11-12T01:00:00Z')))
    WHERE data_id = r2;

    -- R3: 서명 전 메모
    INSERT INTO data (project_id, uploaded_by, type, metadata, url) VALUES
        (p1, u_w1, 'NOTE', jsonb_build_object(
            'schemaVersion', 1,
            'title', '5층 콘크리트 타설 일정',
            'description', '11/20 오전 7시 타설 예정, 레미콘 12대',
            'process', '철근콘크리트공사',
            'capturedAt', '2026-11-13T00:00:00Z',
            'location', jsonb_build_object('site', 'A동', 'building', '본관', 'floor', '5F'),
            'createdAt', '2026-11-13T00:00:00Z',
            'updatedAt', '2026-11-13T00:00:00Z',
            'updatedBy', u_w1::text,
            'files', '[]'::jsonb,
            'confirmations', '[]'::jsonb,
            'aiAnalyses', '[]'::jsonb),
         NULL)
    RETURNING data_id INTO r3;

    -- R4: R1의 정정 기록 (AMENDMENT)
    INSERT INTO data (project_id, uploaded_by, type, metadata, url) VALUES
        (p1, u_w1, 'PHOTO', jsonb_build_object(
            'schemaVersion', 1,
            'title', '4층 슬래브 철근 배근',
            'description', 'D13 @200 배근 완료 (층 정보 정정)',
            'process', '철근콘크리트공사',
            'capturedAt', '2026-11-14T01:40:00Z',
            'location', loc_4f,
            'createdAt', '2026-11-14T02:00:00Z',
            'updatedAt', '2026-11-14T02:01:00Z',
            'updatedBy', u_w1::text,
            'files', jsonb_build_array(pg_temp.file_entry(s_photo3, 64, 48, u_w1, '2026-11-14T02:01:00Z')),
            'confirmations', '[]'::jsonb,
            'aiAnalyses', '[]'::jsonb,
            'correction', jsonb_build_object(
                'kind', 'AMENDMENT',
                'originalDataId', r1,
                'rootDataId', r1,
                'reason', '층 정보 오기재 (3F → 4F)',
                'correctedBy', u_w1::text,
                'correctedAt', '2026-11-14T02:00:00Z')),
         NULL)
    RETURNING data_id INTO r4;

    -- R5: R2의 무효화 기록 (INVALIDATION, CorrectionService와 같은 형태)
    INSERT INTO data (project_id, uploaded_by, type, metadata, url) VALUES
        (p1, u_con, 'INVALIDATION', jsonb_build_object(
            'schemaVersion', 1,
            'title', '기록 #' || r2 || ' 무효화',
            'description', '재시공으로 기존 검측 기록 무효',
            'capturedAt', '2026-11-15T03:00:00Z',
            'location', loc_b2,
            'createdAt', '2026-11-15T03:00:00Z',
            'updatedAt', '2026-11-15T03:00:00Z',
            'updatedBy', u_con::text,
            'files', '[]'::jsonb,
            'confirmations', '[]'::jsonb,
            'aiAnalyses', '[]'::jsonb,
            'correction', jsonb_build_object(
                'kind', 'INVALIDATION',
                'originalDataId', r2,
                'rootDataId', r2,
                'reason', '재시공으로 기존 검측 기록 무효',
                'correctedBy', u_con::text,
                'correctedAt', '2026-11-15T03:00:00Z')),
         NULL)
    RETURNING data_id INTO r5;

    -- R6: Soft Delete 된 기록
    INSERT INTO data (project_id, uploaded_by, type, metadata, url) VALUES
        (p1, u_w2, 'NOTE', jsonb_build_object(
            'schemaVersion', 1,
            'title', '자재 반입 메모 (중복)',
            'description', '철근 HD13 20톤 반입',
            'process', '자재관리',
            'capturedAt', '2026-11-16T00:00:00Z',
            'createdAt', '2026-11-16T00:00:00Z',
            'updatedAt', '2026-11-16T00:10:00Z',
            'updatedBy', u_w2::text,
            'files', '[]'::jsonb,
            'confirmations', '[]'::jsonb,
            'aiAnalyses', '[]'::jsonb,
            'deletion', jsonb_build_object('deletedAt', '2026-11-16T00:10:00Z',
                                           'deletedBy', u_w2::text, 'reason', '중복 등록')),
         NULL)
    RETURNING data_id INTO r6;

    -- R7: 사진 + AI 분석 실패 이력, 서명 전
    INSERT INTO data (project_id, uploaded_by, type, metadata, url) VALUES
        (p1, u_w1, 'PHOTO', jsonb_build_object(
            'schemaVersion', 1,
            'title', 'B동 남측 커튼월 설치',
            'description', '멀리언 고정 앵커 시공 상태',
            'process', '커튼월공사',
            'capturedAt', '2026-11-17T03:30:00Z',
            'location', loc_b_wall,
            'createdAt', '2026-11-17T04:00:00Z',
            'updatedAt', '2026-11-17T04:01:00Z',
            'updatedBy', u_w1::text,
            'files', jsonb_build_array(pg_temp.file_entry(s_photo4, 64, 48, u_w1, '2026-11-17T04:01:00Z')),
            'confirmations', '[]'::jsonb,
            'aiAnalyses', jsonb_build_array(jsonb_build_object(
                'analysisId', 'a0000000-0000-4000-8000-000000000002',
                'version', 1,
                'status', 'FAILED',
                'model', 'facade-inspector-v1',
                'targetStorageIds', jsonb_build_array(s_photo4),
                'errorCode', 'AI_SERVER_TIMEOUT',
                'errorMessage', 'AI 서버 응답 시간이 초과되었습니다.',
                'requestedBy', u_w1::text,
                'requestedAt', '2026-11-17T04:05:00Z',
                'completedAt', '2026-11-17T04:06:00Z'))),
         NULL)
    RETURNING data_id INTO r7;

    -- R8: 프로젝트 2 메모
    INSERT INTO data (project_id, uploaded_by, type, metadata, url) VALUES
        (p2, u_w1, 'NOTE', jsonb_build_object(
            'schemaVersion', 1,
            'title', '증축 부지 현황 조사',
            'description', '기존 동 북측 주차장 부지, 지하 매설물 확인 필요',
            'process', '가설공사',
            'capturedAt', '2026-11-18T00:00:00Z',
            'location', jsonb_build_object('site', '증축동', 'address', '경기도 성남시 분당구 판교로 45'),
            'createdAt', '2026-11-18T00:00:00Z',
            'updatedAt', '2026-11-18T00:00:00Z',
            'updatedBy', u_w1::text,
            'files', '[]'::jsonb,
            'confirmations', '[]'::jsonb,
            'aiAnalyses', '[]'::jsonb),
         NULL)
    RETURNING data_id INTO r8;

    RAISE NOTICE 'projects: P1=%, P2=%, P3(삭제됨)=%', p1, p2, p3;
    RAISE NOTICE 'records: R1=%, R2=%, R3=%, R4=%, R5=%, R6=%, R7=%, R8=%', r1, r2, r3, r4, r5, r6, r7, r8;
    RAISE NOTICE 'storage: photo=%,%,%,%  signature=%,%', s_photo1, s_photo2, s_photo3, s_photo4, s_sig1, s_sig2;
END
$$;

COMMIT;


-- ============================================
-- [정리] dummy 데이터 삭제 (필요할 때만 주석 해제 후 실행)
-- FK 순서: data → storage(dummy 기록이 참조하던 것) → project → account
-- ============================================
-- BEGIN;
-- CREATE TEMP TABLE dummy_storage_ids ON COMMIT DROP AS
--     SELECT (f ->> 'storageId')::bigint AS storage_id
--     FROM data d, jsonb_array_elements(coalesce(d.metadata -> 'files', '[]'::jsonb)) f
--     WHERE d.uploaded_by IN (SELECT uid FROM account WHERE login_id LIKE 'dummy\_%')
--     UNION
--     SELECT (c -> 'signature' ->> 'storageId')::bigint
--     FROM data d, jsonb_array_elements(coalesce(d.metadata -> 'confirmations', '[]'::jsonb)) c
--     WHERE d.uploaded_by IN (SELECT uid FROM account WHERE login_id LIKE 'dummy\_%')
--       AND c -> 'signature' IS NOT NULL;
-- DELETE FROM data
--     WHERE project_id IN (SELECT project_id FROM project
--                          WHERE created_by IN (SELECT uid FROM account WHERE login_id LIKE 'dummy\_%'))
--        OR uploaded_by IN (SELECT uid FROM account WHERE login_id LIKE 'dummy\_%');
-- DELETE FROM storage WHERE storage_id IN (SELECT storage_id FROM dummy_storage_ids);
-- DELETE FROM project WHERE created_by IN (SELECT uid FROM account WHERE login_id LIKE 'dummy\_%');
-- DELETE FROM account WHERE login_id LIKE 'dummy\_%';
-- COMMIT;
