package com.example.backend;

import com.example.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨텍스트 로딩 시 ddl-auto=validate로 Entity와 실제 스키마(create_table.sql)의 일치를 검증한다.
 */
class BackendApplicationTests extends IntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void openApiDocsArePublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

}
