package com.invoiceapp.backend;

import com.invoiceapp.backend.config.JacksonTestConfig;
import com.invoiceapp.backend.config.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(JacksonTestConfig.class)
class BackendApplicationTests extends PostgresTestContainer {

	@Test
	void contextLoads() {
	}
}
