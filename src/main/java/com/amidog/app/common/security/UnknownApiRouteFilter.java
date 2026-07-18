package com.amidog.app.common.security;

import com.amidog.app.common.api.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class UnknownApiRouteFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1";
    private static final String NOT_FOUND_MESSAGE =
            "No se encontró el recurso solicitado.";
    private static final Set<String> SECURITY_OWNED_POST_ROUTES = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/logout");

    private final RequestMappingHandlerMapping handlerMapping;
    private final ObjectMapper objectMapper;

    public UnknownApiRouteFilter(
            RequestMappingHandlerMapping handlerMapping,
            ObjectMapper objectMapper) {
        this.handlerMapping = handlerMapping;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = applicationPath(request);
        if (isApiPath(path)
                && !isSecurityOwnedRoute(request, path)
                && !hasControllerRoute(request)) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(
                    response.getOutputStream(),
                    ApiErrorResponse.of("NOT_FOUND", NOT_FOUND_MESSAGE));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasControllerRoute(HttpServletRequest request) {
        try {
            return handlerMapping.getHandler(request) != null;
        } catch (Exception knownRouteMappingFailure) {
            return true;
        }
    }

    private static String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty()
                ? uri
                : uri.substring(contextPath.length());
    }

    private static boolean isApiPath(String path) {
        return path.equals(API_PREFIX)
                || path.startsWith(API_PREFIX + "/");
    }

    private static boolean isSecurityOwnedRoute(
            HttpServletRequest request, String path) {
        return "POST".equals(request.getMethod())
                && SECURITY_OWNED_POST_ROUTES.contains(path);
    }
}
