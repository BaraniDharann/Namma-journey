package com.travelplatform.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Found by the live negative tests: a broken JSON body or a wrong type in a field
 * ("latitude": "abc") answered 500. It is the caller's mistake: 400, with nothing internal shown.
 */
class BadRequestBodyTest {

    @RestController
    static class Probe {
        @PostMapping("/probe")
        Map<String, Double> probe(@RequestBody Map<String, Double> body) {
            return body;
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("broken JSON is a 400 that names no parser or class")
    void brokenJson() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    @DisplayName("text where a number belongs is a 400")
    void wrongType() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("wrong content type is 415 and wrong method 405, not 500")
    void wrongTypeOrMethod() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/probe")).andExpect(status().isMethodNotAllowed());
    }
}
