package com.invoiceapp.backend;

import com.invoiceapp.backend.config.JacksonTestConfig;
import com.invoiceapp.backend.config.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(JacksonTestConfig.class)
class BackendApplicationTests extends PostgresTestContainer {

	@MockitoBean
	private KafkaTemplate<String, String> kafkaTemplate;

	@Test
	void contextLoads() {
	}
}
