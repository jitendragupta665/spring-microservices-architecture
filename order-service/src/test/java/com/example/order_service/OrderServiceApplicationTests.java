package com.example.order_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "eureka.client.enabled=false") // no Eureka needed to check the context starts
class OrderServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
