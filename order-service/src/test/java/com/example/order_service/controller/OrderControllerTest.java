package com.example.order_service.controller;

import com.example.order_service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-LAYER test for order-service: only the controller + Spring MVC, OrderService mocked.
 * Proves the HTTP mapping (GET /order -> 200 + body) without touching user-service.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void getOrderReturnsTheServiceResult() throws Exception {
        when(orderService.createOrder()).thenReturn("Order created for -> x");

        mvc.perform(get("/order"))
                .andExpect(status().isOk())
                .andExpect(content().string("Order created for -> x"));
    }
}
