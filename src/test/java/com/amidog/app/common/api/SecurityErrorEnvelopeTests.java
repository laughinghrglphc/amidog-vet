package com.amidog.app.common.api;

import com.amidog.app.common.security.RateLimitService;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(SecurityErrorEnvelopeTests.TestConfiguration.class)
@WebAppConfiguration
class SecurityErrorEnvelopeTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticatedRequestsUseTheUnifiedUtf8Envelope()
            throws Exception {
        mvc.perform(get("/api/v1/me/security-contract"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(
                        "application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message")
                        .value("Autenticación requerida."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void wrongRoleAndMissingCsrfUseTheUnifiedUtf8Envelope()
            throws Exception {
        mvc.perform(get("/api/v1/admin/security-contract")
                        .with(user("client").roles("CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(
                        "application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso denegado."))
                .andExpect(jsonPath("$.errors").isEmpty());

        mvc.perform(post("/api/v1/admin/security-contract")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(
                        "application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso denegado."))
                .andExpect(jsonPath("$.errors").isEmpty());

        for (String securityOwnedRoute : new String[]{
                "/api/v1/auth/login",
                "/api/v1/auth/logout"}) {
            mvc.perform(post(securityOwnedRoute))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentType(
                            "application/json;charset=UTF-8"))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.message")
                            .value("Acceso denegado."))
                    .andExpect(jsonPath("$.errors").isEmpty());
        }
    }

    @Test
    void anonymousUnknownApiRoutesUse404BeforeAuthenticationAndCsrf()
            throws Exception {
        assertReviewedNotFound(
                mvc.perform(get("/api/v1/route-that-does-not-exist")));
        assertReviewedNotFound(
                mvc.perform(get("/api/v1/admin/route-that-does-not-exist")));
        assertReviewedNotFound(
                mvc.perform(get("/api/v1/me/route-that-does-not-exist")));
        assertReviewedNotFound(
                mvc.perform(post("/api/v1/admin/route-that-does-not-exist")));
    }

    @Test
    void authenticatedUnknownApiRoutesUse404AcrossProtectedPrefixes()
            throws Exception {
        assertReviewedNotFound(
                mvc.perform(get("/api/v1/route-that-does-not-exist")
                        .with(user("admin").roles("ADMIN"))));
        assertReviewedNotFound(
                mvc.perform(get("/api/v1/me/route-that-does-not-exist")
                        .with(user("client").roles("CLIENT"))));
    }

    private static void assertReviewedNotFound(
            org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        result
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("No se encontró el recurso solicitado."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ApiExceptionHandler.class})
    static class TestConfiguration {

        @Bean
        SecurityContractController securityContractController() {
            return new SecurityContractController();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RateLimitService rateLimitService() {
            return new RateLimitService(Clock.systemUTC());
        }

        @Bean
        AmidogProperties amidogProperties() {
            return new AmidogProperties(
                    ZoneId.of("America/Santiago"),
                    URI.create("http://localhost:5173"),
                    URI.create("http://localhost:5173"),
                    new AmidogProperties.Booking(false, 30, 2, 90),
                    new AmidogProperties.Admin("", "", "", ""),
                    new AmidogProperties.Contact("", ""),
                    new AmidogProperties.Email("log"));
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("client")
                            .password("{noop}password")
                            .roles("CLIENT")
                            .build(),
                    User.withUsername("admin")
                            .password("{noop}password")
                            .roles("ADMIN")
                            .build());
        }
    }

    @RestController
    static class SecurityContractController {

        @GetMapping("/api/v1/me/security-contract")
        void clientGet() {
        }

        @GetMapping("/api/v1/admin/security-contract")
        void adminGet() {
        }

        @PostMapping("/api/v1/admin/security-contract")
        void adminPost() {
        }
    }
}
