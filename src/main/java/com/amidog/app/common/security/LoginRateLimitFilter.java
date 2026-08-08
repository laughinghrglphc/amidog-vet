package com.amidog.app.common.security;

import com.amidog.app.common.api.ApiErrorResponse;
import com.amidog.app.common.api.TooManyRequestsException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class LoginRateLimitFilter extends OncePerRequestFilter {

    static final int LOGIN_LIMIT = 10;
    static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final RateLimitService rateLimits;
    private final ObjectMapper objectMapper;
    private final RequestMatcher loginRequest =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, LOGIN_PATH);

    public LoginRateLimitFilter(RateLimitService rateLimits, ObjectMapper objectMapper) {
        this.rateLimits = rateLimits;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !loginRequest.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String address = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (rateLimits.tryAcquire("login:" + address, LOGIN_LIMIT, LOGIN_WINDOW)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of("RATE_LIMITED", TooManyRequestsException.MESSAGE));
    }
}
