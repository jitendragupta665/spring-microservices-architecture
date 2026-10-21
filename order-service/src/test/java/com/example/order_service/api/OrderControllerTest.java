package com.example.order_service.api;

import com.example.order_service.domain.Order;
import com.example.order_service.domain.OrderService;
import com.example.order_service.domain.UnknownUserException;
import com.example.order_service.domain.UserServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-LAYER test: only Spring MVC + the controller + ApiErrors, OrderService mocked.
 * It pins down the HTTP CONTRACT clients rely on: paths, methods, status codes, headers, JSON.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final Instant T = Instant.parse("2026-10-22T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orderService;

    private org.springframework.test.web.servlet.ResultActions postOrder(String json) throws Exception {
        return mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createReturns201WithLocationAndTheOrder() throws Exception {
        when(orderService.placeOrder(1, "book")).thenReturn(new Order(5, 1, "book", T));

        postOrder("{\"userId\":1,\"item\":\"book\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.item").value("book"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-22T10:00:00Z"));
    }

    @ParameterizedTest(name = "invalid body {0} -> 400")
    @ValueSource(strings = {
            "{\"item\":\"book\"}",                 // userId missing
            "{\"userId\":0,\"item\":\"book\"}",    // userId not positive
            "{\"userId\":1}",                      // item missing
            "{\"userId\":1,\"item\":\"  \"}",      // item blank
            "not json"})
    void invalidBodyIs400AndNeverReachesTheService(String json) throws Exception {
        postOrder(json)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verify(orderService, never()).placeOrder(anyLong(), anyString());
    }

    @Test
    void unknownUserIs422() throws Exception {
        when(orderService.placeOrder(99, "book")).thenThrow(new UnknownUserException(99));

        postOrder("{\"userId\":99,\"item\":\"book\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("user 99 does not exist"));
    }

    @Test
    void userServiceDownIs503WithRetryAfterAndNoInternalDetails() throws Exception {
        when(orderService.placeOrder(1, "book")).thenThrow(
                new UserServiceUnavailableException("I/O error on GET http://user-service:8080/...", null));

        postOrder("{\"userId\":1,\"item\":\"book\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.detail").value("user-service is unavailable, try again later"));
    }

    @Test
    void getExistingOrderIs200() throws Exception {
        when(orderService.findById(5)).thenReturn(Optional.of(new Order(5, 1, "book", T)));

        mvc.perform(get("/api/v1/orders/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item").value("book"));
    }

    @Test
    void getMissingOrderIs404ProblemJson() throws Exception {
        when(orderService.findById(7)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/orders/7"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("order 7 does not exist"));
    }

    @Test
    void creatingWithGetIsNotAllowed() throws Exception {
        // The old API created orders on GET /order. GET /api/v1/orders (no id) is not a route.
        mvc.perform(get("/api/v1/orders")).andExpect(status().isMethodNotAllowed());
    }
}
