package com.amidog.app.config;

import com.amidog.app.auth.AuthDtos;
import com.amidog.app.common.api.ApiErrorResponse;
import com.amidog.app.common.security.LoginRateLimitFilter;
import com.amidog.app.common.security.RateLimitService;
import com.amidog.app.common.security.UnknownApiRouteFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    public static final String GENERIC_LOGIN_FAILURE =
            "Correo o contraseña incorrectos, o cuenta aún no verificada.";
    private static final String AUTHENTICATION_REQUIRED = "Autenticación requerida.";
    private static final String ACCESS_DENIED = "Acceso denegado.";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            RateLimitService rateLimits,
            RequestMappingHandlerMapping handlerMapping,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                .addFilter(apiCorsFilter(
                        corsConfigurationSource, objectMapper))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/auth/csrf", "/api/v1/services", "/api/v1/availability").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/register",
                                "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password", "/api/v1/contact").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/me/**").hasRole("CLIENT")
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) ->
                                writeJson(objectMapper, response, HttpStatus.OK, AuthDtos.AuthResponse.from(authentication)))
                        .failureHandler((request, response, exception) ->
                                writeJson(objectMapper, response, HttpStatus.UNAUTHORIZED,
                                        ApiErrorResponse.of("INVALID_CREDENTIALS", GENERIC_LOGIN_FAILURE))))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("SESSION", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value())))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeJson(objectMapper, response,
                                HttpStatus.UNAUTHORIZED,
                                ApiErrorResponse.of("UNAUTHENTICATED", AUTHENTICATION_REQUIRED)))
                        .accessDeniedHandler((request, response, exception) -> writeJson(objectMapper, response,
                                HttpStatus.FORBIDDEN,
                                ApiErrorResponse.of("FORBIDDEN", ACCESS_DENIED))))
                .addFilterBefore(
                        new LoginRateLimitFilter(rateLimits, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new UnknownApiRouteFilter(
                                handlerMapping, objectMapper),
                        CsrfFilter.class)
                .headers(headers -> headers
                        .contentTypeOptions(withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(policy -> policy.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy
                                        .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AmidogProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                properties.frontendOrigin().toASCIIString()));
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Content-Type", "X-XSRF-TOKEN"));

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static CorsFilter apiCorsFilter(
            CorsConfigurationSource source,
            ObjectMapper objectMapper) {
        CorsFilter filter = new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response)
                    throws IOException {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(
                        new MediaType(
                                MediaType.APPLICATION_JSON,
                                StandardCharsets.UTF_8));
                objectMapper.writeValue(
                        response.getBody(),
                        ApiErrorResponse.of("FORBIDDEN", ACCESS_DENIED));
                response.flush();
            }
        });
        return filter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder = (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }

    private static void writeJson(ObjectMapper objectMapper, HttpServletResponse response,
                                  HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
