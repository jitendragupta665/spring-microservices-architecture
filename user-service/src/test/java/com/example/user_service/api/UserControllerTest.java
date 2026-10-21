package com.example.user_service.api;

import com.example.user_service.domain.User;
import com.example.user_service.domain.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-LAYER ("slice") test: starts only Spring MVC (controllers, JSON, error handling) with a
 * fake HTTP request (MockMvc), no real server, no other beans. Checks the HTTP contract:
 * path, status code, headers, JSON body. Much faster than a full @SpringBootTest.
 */
@WebMvcTest(controllers = UserController.class, properties = "HOSTNAME=pod-a")
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    // The controller depends on UserService; the slice does not create services, so we
    // provide a Mockito mock in its place.
    @MockitoBean
    private UserService userService;

    @Test
    void knownUserIs200JsonAndNamesThePod() throws Exception {
        when(userService.findById(1)).thenReturn(Optional.of(new User(1, "Rahul", 22)));

        mvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Served-By", "pod-a"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Rahul"))
                .andExpect(jsonPath("$.age").value(22));
    }

    @Test
    void unknownUserIs404ProblemJson() throws Exception {
        when(userService.findById(99)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("user 99 does not exist"));
    }

    @Test
    void nonNumericIdIs400() throws Exception {
        mvc.perform(get("/api/v1/users/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void oldPathIsGone() throws Exception {
        mvc.perform(get("/user")).andExpect(status().isNotFound());
    }
}
