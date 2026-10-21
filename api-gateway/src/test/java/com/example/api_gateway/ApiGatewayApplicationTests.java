package com.example.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "eureka.client.enabled=false") // no Eureka needed to check the context starts
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
