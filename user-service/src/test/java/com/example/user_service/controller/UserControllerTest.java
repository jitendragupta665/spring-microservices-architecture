package com.example.user_service.controller;

import com.example.user_service.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-LAYER ("slice") test: starts only Spring MVC (controllers, JSON, error handling) with a
 * fake HTTP request (MockMvc), no real server, no Eureka, no other beans. Checks the HTTP
 * contract: path, status code, body. Much faster than a full @SpringBootTest.
 */
@WebMvcTest(controllers = UserController.class, properties = "server.port=8080")
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    // The controller depends on UserService; the slice does not create services, so we
    // provide a Mockito mock in its place.
    @MockitoBean
    private UserService userService;

    @Test
    void getUserReturns200AndNamesThePort() throws Exception {
        mvc.perform(get("/user"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("USER-SERVICE running on port: 8080")));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mvc.perform(get("/users"))
                .andExpect(status().isNotFound());
    }
}
