package com.amidog.app.auth;

import com.amidog.app.client.ClientRepository;
import com.amidog.app.support.PostgresIntegrationTest;
import com.amidog.app.support.RateLimitTestSupport;
import com.amidog.app.common.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.IntStream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthRateLimitIntegrationTests extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired ClientRepository clients;
    @Autowired RateLimitService rateLimits;

    @BeforeEach
    void cleanDatabase() {
        RateLimitTestSupport.reset(rateLimits);
        clients.deleteAll();
        users.deleteAll();
    }

    @Test
    void registerAllowsFiveRequestsPerRemoteAddressAndRejectsTheSixth() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/auth/register")
                            .with(csrf())
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.11");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson("client" + attempt + "@example.cl")))
                    .andExpect(status().isAccepted());
        }

        mvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.11");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("blocked@example.cl")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void resendAndForgotUseIndependentNormalizedEmailLimits() throws Exception {
        IntStream.range(0, 3).forEach(attempt -> {
            try {
                resend(" Rate.Limited@Example.cl ").andExpect(status().isAccepted());
                forgot(" RATE.LIMITED@example.cl ").andExpect(status().isAccepted());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });

        resend("rate.limited@example.cl")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        forgot("rate.limited@example.cl")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private org.springframework.test.web.servlet.ResultActions resend(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/resend-verification")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private String registerJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"Correct-Horse-9!\","
                + "\"name\":\"Ana Pérez\",\"phone\":\"+56912345678\"}";
    }
}
