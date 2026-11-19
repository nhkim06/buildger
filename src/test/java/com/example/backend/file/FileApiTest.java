package com.example.backend.file;

import com.example.backend.global.common.Hashes;
import com.example.backend.support.IntegrationTest;
import com.example.backend.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileApiTest extends IntegrationTest {

    private String owner;
    private String member;
    private String otherMember;
    private String viewer;
    private long projectId;
    private long dataId;

    @BeforeEach
    void setUpRecord() throws Exception {
        owner = signupAndLogin("lee.contractor", 2);
        member = signupAndLogin("kim.site01", 1);
        otherMember = signupAndLogin("park.site02", 1);
        viewer = signupAndLogin("choi.viewer", 3);
        projectId = createProject(owner, "현장");
        addMember(owner, projectId, "kim.site01", "MEMBER");
        addMember(owner, projectId, "park.site02", "MEMBER");
        addMember(owner, projectId, "choi.viewer", "VIEWER");
        dataId = createRecord(member, projectId, "기록");
    }

    private String filesUrl() {
        return "/api/projects/" + projectId + "/records/" + dataId + "/files";
    }

    private MockMultipartHttpServletRequestBuilder upload(String token, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder builder = multipart(filesUrl());
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        builder.header("Authorization", bearer(token));
        return builder;
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("files", name, contentType, bytes);
    }

    @Test
    void upload_jpegAndPng_storesBinaryAndMetadata() throws Exception {
        byte[] png = TestImages.png(1);
        byte[] jpeg = TestImages.jpeg(2);

        mockMvc.perform(upload(member, file("slab.png", "image/png", png), file("rebar.JPG", "image/jpeg", jpeg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].storageId").value(1))
                .andExpect(jsonPath("$[0].contentType").value("image/png"))
                .andExpect(jsonPath("$[0].width").value(32))
                .andExpect(jsonPath("$[0].height").value(24))
                .andExpect(jsonPath("$[0].sha256").value(Hashes.sha256Hex(png)))
                .andExpect(jsonPath("$[0].downloadUrl").value("/api/files/1"))
                .andExpect(jsonPath("$[1].contentType").value("image/jpeg"));

        byte[] stored = jdbcTemplate.queryForObject("SELECT image FROM storage WHERE storage_id = 1", byte[].class);
        assertThat(stored).isEqualTo(png);
        String name = jdbcTemplate.queryForObject("SELECT name FROM storage WHERE storage_id = 2", String.class);
        assertThat(name).isEqualTo("rebar.JPG");
        JsonNode files = jsonMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT metadata->'files' FROM data WHERE data_id = ?", String.class, dataId));
        assertThat(files.size()).isEqualTo(2);
        assertThat(files.get(1).get("sha256").asString()).isEqualTo(Hashes.sha256Hex(jpeg));
        assertThat(files.get(0).get("uploadedBy").asString()).isEqualTo(uidOf("kim.site01"));
    }

    @Test
    void upload_nonImageOrDisguisedFile_returns415() throws Exception {
        mockMvc.perform(upload(member, file("note.png", "image/png", "hello".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        // MP4 (ftyp 박스) — 영상은 허용하지 않는다.
        byte[] mp4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
        mockMvc.perform(upload(member, file("clip.mp4", "video/mp4", mp4)))
                .andExpect(status().isUnsupportedMediaType());
        // PNG 내용인데 확장자가 jpg
        mockMvc.perform(upload(member, file("photo.jpg", "image/jpeg", TestImages.png(3))))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM storage", Integer.class)).isZero();
    }

    @Test
    void upload_corruptedOrOversizedImage_isRejected() throws Exception {
        byte[] corrupted = Arrays.copyOf(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, 64);
        mockMvc.perform(upload(member, file("broken.png", "image/png", corrupted)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        mockMvc.perform(upload(member, file("huge.png", "image/png", TestImages.png(4, 10_001, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        byte[] tooLarge = Arrays.copyOf(TestImages.png(5), 10 * 1024 * 1024 + 1);
        mockMvc.perform(upload(member, file("large.png", "image/png", tooLarge)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        mockMvc.perform(upload(member, file("empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_duplicateImage_returns409() throws Exception {
        byte[] png = TestImages.png(1);
        mockMvc.perform(upload(member, file("a.png", "image/png", png), file("b.png", "image/png", png)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FILE"));

        uploadImages(member, projectId, dataId, png);
        mockMvc.perform(upload(member, file("again.png", "image/png", png)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FILE"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM storage", Integer.class)).isEqualTo(1);
    }

    @Test
    void upload_permissionRules() throws Exception {
        mockMvc.perform(upload(viewer, file("a.png", "image/png", TestImages.png(1))))
                .andExpect(status().isForbidden());
        mockMvc.perform(upload(otherMember, file("a.png", "image/png", TestImages.png(1))))
                .andExpect(status().isForbidden());
        mockMvc.perform(upload(owner, file("a.png", "image/png", TestImages.png(1))))
                .andExpect(status().isCreated());
    }

    @Test
    void upload_countLimitPerRecord() throws Exception {
        byte[][] first = new byte[10][];
        byte[][] second = new byte[10][];
        for (int i = 0; i < 10; i++) {
            first[i] = TestImages.png(100 + i);
            second[i] = TestImages.png(200 + i);
        }
        uploadImages(member, projectId, dataId, first);
        uploadImages(member, projectId, dataId, second);

        mockMvc.perform(upload(member, file("21.png", "image/png", TestImages.png(300))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_COUNT_EXCEEDED"));

        MockMultipartFile[] eleven = new MockMultipartFile[11];
        for (int i = 0; i < 11; i++) {
            eleven[i] = file(i + ".png", "image/png", TestImages.png(400 + i));
        }
        mockMvc.perform(upload(owner, eleven)).andExpect(status().isBadRequest());
    }

    @Test
    void list_andDownload_withHeadersAndEtag() throws Exception {
        byte[] png = TestImages.png(1);
        long storageId = uploadImages(member, projectId, dataId, png).get(0);

        mockMvc.perform(get(filesUrl()).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storageId").value(storageId));

        MvcResult result = mockMvc.perform(get("/api/files/" + storageId).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png))
                .andExpect(header().string("ETag", "\"" + Hashes.sha256Hex(png) + "\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("private");
        assertThat(result.getResponse().getHeader("Content-Disposition")).startsWith("inline");

        mockMvc.perform(get("/api/files/" + storageId).param("download", "true").header("Authorization", bearer(viewer)))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        mockMvc.perform(get("/api/files/" + storageId).header("Authorization", bearer(viewer))
                        .header("If-None-Match", "\"" + Hashes.sha256Hex(png) + "\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void download_accessRules() throws Exception {
        long storageId = uploadImages(member, projectId, dataId, TestImages.png(1)).get(0);
        String outsider = signupAndLogin("outsider", 2);

        mockMvc.perform(get("/api/files/" + storageId).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
        mockMvc.perform(get("/api/files/999").header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
        mockMvc.perform(get("/api/files/" + storageId))
                .andExpect(status().isUnauthorized());

        // 연결된 기록이 없는 storage 행은 권한 검증이 불가능하므로 404
        jdbcTemplate.update("INSERT INTO storage(name, image) VALUES ('orphan.png', ?)", (Object) TestImages.png(9));
        Long orphan = jdbcTemplate.queryForObject("SELECT max(storage_id) FROM storage", Long.class);
        mockMvc.perform(get("/api/files/" + orphan).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_softDeletesFileAndKeepsBinary() throws Exception {
        List<Long> ids = uploadImages(member, projectId, dataId, TestImages.png(1), TestImages.png(2));

        mockMvc.perform(delete(filesUrl() + "/" + ids.get(0)).header("Authorization", bearer(otherMember)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(filesUrl() + "/" + ids.get(0)).header("Authorization", bearer(member)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(filesUrl() + "/" + ids.get(0)).header("Authorization", bearer(member)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));

        mockMvc.perform(get(filesUrl()).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/files/" + ids.get(0)).header("Authorization", bearer(member)))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM storage", Integer.class)).isEqualTo(2);

        // 삭제한 이미지와 같은 이미지는 다시 업로드할 수 있다.
        uploadImages(member, projectId, dataId, TestImages.png(1));
    }

    @Test
    void signedRecord_blocksUploadAndDelete() throws Exception {
        long storageId = uploadImages(member, projectId, dataId, TestImages.png(1)).get(0);
        jdbcTemplate.update("UPDATE data SET metadata = jsonb_set(metadata, '{confirmations}', "
                + "CAST('[{\"confirmationId\":\"t\",\"status\":\"APPROVED\"}]' AS jsonb)) WHERE data_id = ?", dataId);

        mockMvc.perform(upload(member, file("b.png", "image/png", TestImages.png(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_LOCKED"));
        mockMvc.perform(delete(filesUrl() + "/" + storageId).header("Authorization", bearer(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_LOCKED"));
    }

    @Test
    void recordDetail_verifyFiles_detectsTamperedBinary() throws Exception {
        long storageId = uploadImages(member, projectId, dataId, TestImages.png(1)).get(0);
        String detailUrl = "/api/projects/" + projectId + "/records/" + dataId;

        mockMvc.perform(get(detailUrl).header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.integrity.fileStatus").value("NOT_CHECKED"));
        mockMvc.perform(get(detailUrl).param("verifyFiles", "true").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.integrity.fileStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.files[0].storageId").value(storageId));

        jdbcTemplate.update("UPDATE storage SET image = ? WHERE storage_id = ?", TestImages.png(2), storageId);
        mockMvc.perform(get(detailUrl).param("verifyFiles", "true").header("Authorization", bearer(member)))
                .andExpect(jsonPath("$.integrity.fileStatus").value("MISMATCH"));
    }
}
