package com.amidog.app.common.security;

import com.amidog.app.common.api.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTests {

    private final MutableClock clock = new MutableClock(
            Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsTenLoginAttemptsThenReturnsUnified429Json() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new RateLimitService(clock), objectMapper);
        AtomicInteger continued = new AtomicInteger();

        for (int attempt = 0; attempt < 10; attempt++) {
            MockHttpServletResponse response = invoke(filter, "/api/v1/auth/login", "POST",
                    "198.51.100.24", continued);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse rejected = invoke(filter, "/api/v1/auth/login", "POST",
                "198.51.100.24", continued);
        ApiErrorResponse body = objectMapper.readValue(rejected.getContentAsByteArray(), ApiErrorResponse.class);

        assertThat(continued).hasValue(10);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getContentType()).startsWith("application/json");
        assertThat(rejected.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        assertThat(body.code()).isEqualTo("RATE_LIMITED");
        assertThat(body.errors()).isEmpty();
    }

    @Test
    void resetsAtTheExactFifteenMinuteBoundary() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new RateLimitService(clock), objectMapper);
        AtomicInteger continued = new AtomicInteger();

        for (int attempt = 0; attempt < 10; attempt++) {
            invoke(filter, "/api/v1/auth/login", "POST", "198.51.100.24", continued);
        }
        clock.advance(Duration.ofMinutes(15));

        MockHttpServletResponse response = invoke(filter, "/api/v1/auth/login", "POST",
                "198.51.100.24", continued);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(continued).hasValue(11);
    }

    @Test
    void ignoresOtherMethodsAndRoutes() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new RateLimitService(clock), objectMapper);
        AtomicInteger continued = new AtomicInteger();

        for (int attempt = 0; attempt < 20; attempt++) {
            invoke(filter, "/api/v1/auth/login", HttpMethod.GET.name(), "198.51.100.24", continued);
            invoke(filter, "/api/v1/auth/register", HttpMethod.POST.name(), "198.51.100.24", continued);
        }

        assertThat(continued).hasValue(40);
    }

    @Test
    void contextPathLoginUsesTheSameExactPathSemanticsAndCannotBypassTheLimit() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new RateLimitService(clock), objectMapper);
        AtomicInteger continued = new AtomicInteger();

        for (int attempt = 0; attempt < 10; attempt++) {
            MockHttpServletResponse response = invokeWithContextPath(
                    filter, "/amidog", "/api/v1/auth/login", "POST",
                    "198.51.100.25", continued);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse rejected = invokeWithContextPath(
                filter, "/amidog", "/api/v1/auth/login", "POST",
                "198.51.100.25", continued);

        assertThat(continued).hasValue(10);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(objectMapper.readValue(
                rejected.getContentAsByteArray(), ApiErrorResponse.class).code())
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    void similarPrefixAndTrailingSlashRoutesRemainUnmatched() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(new RateLimitService(clock), objectMapper);
        AtomicInteger continued = new AtomicInteger();

        for (int attempt = 0; attempt < 20; attempt++) {
            invokeWithContextPath(filter, "/amidog", "/api/v1/auth/login-extra", "POST",
                    "198.51.100.26", continued);
            invokeWithContextPath(filter, "/amidog", "/api/v1/auth/login/", "POST",
                    "198.51.100.26", continued);
        }

        assertThat(continued).hasValue(40);
    }

    private MockHttpServletResponse invoke(
            LoginRateLimitFilter filter,
            String uri,
            String method,
            String remoteAddress,
            AtomicInteger continued
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response,
                (servletRequest, servletResponse) -> continued.incrementAndGet());
        return response;
    }

    private MockHttpServletResponse invokeWithContextPath(
            LoginRateLimitFilter filter,
            String contextPath,
            String servletPath,
            String method,
            String remoteAddress,
            AtomicInteger continued
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                method, contextPath + servletPath);
        request.setContextPath(contextPath);
        request.setServletPath(servletPath);
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response,
                (servletRequest, servletResponse) -> continued.incrementAndGet());
        return response;
    }
}
