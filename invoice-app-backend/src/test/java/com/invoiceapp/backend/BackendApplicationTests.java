package com.invoiceapp.backend;

import com.invoiceapp.backend.config.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendApplicationTests extends PostgresTestContainer {

	@Test
	void contextLoads() {
	}
}
