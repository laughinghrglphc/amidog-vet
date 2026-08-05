package com.amidog.app.security;

import com.amidog.app.common.api.ApiExceptionHandler;
import com.amidog.app.common.security.RateLimitService;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.session.jdbc.autoconfigure.JdbcSessionProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityRegressionIntegrationTests.SecurityProbeController.class)
@ContextConfiguration(classes = {
        SecurityRegressionIntegrationTests.SecurityTestApplication.class,
        SecurityRegressionIntegrationTests.SecurityProbeController.class
})
@EnableConfigurationProperties(AmidogProperties.class)
@Import({
        SecurityConfig.class,
        ApiExceptionHandler.class,
        RateLimitService.class,
        SecurityRegressionIntegrationTests.SecurityTestDependencies.class
})
@TestPropertySource(properties = {
        "amidog.frontend-base-url=https://links.amidog.cl/application/",
        "amidog.frontend-origin=https://app.amidog.cl"
})
class SecurityRegressionIntegrationTests {

    private static final String CONFIGURED_ORIGIN = "https://app.amidog.cl";

    @Autowired
    MockMvc mvc;

    @Test
    void permitsOnlyTheConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/me")
                        .header("Origin", CONFIGURED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin", CONFIGURED_ORIGIN));

    }

    @Test
    void credentialedPreflightAllowsOnlyReviewedMethodsAndHeaders()
            throws Exception {
        mvc.perform(options("/api/v1/me/profile")
                        .header("Origin", CONFIGURED_ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header(
                                "Access-Control-Request-Headers",
                                "Content-Type, X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin", CONFIGURED_ORIGIN))
                .andExpect(header().string(
                        "Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string(
                        "Access-Control-Allow-Methods",
                        "GET,POST,PATCH,PUT,DELETE,OPTIONS"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        "Content-Type, X-XSRF-TOKEN"));

    }

    @Test
    void hostileOriginUsesTheClosedJson403Envelope() throws Exception {
        assertClosedCorsRejection(
                mvc.perform(options("/api/v1/auth/me")
                        .header("Origin", "https://evil.invalid")
                        .header("Access-Control-Request-Method", "GET")));
    }

    @Test
    void unreviewedMethodUsesTheClosedJson403Envelope() throws Exception {
        assertClosedCorsRejection(
                mvc.perform(options("/api/v1/me/profile")
                        .header("Origin", CONFIGURED_ORIGIN)
                        .header("Access-Control-Request-Method", "TRACE")));
    }

    @Test
    void unreviewedHeaderUsesTheClosedJson403Envelope() throws Exception {
        assertClosedCorsRejection(
                mvc.perform(options("/api/v1/me/profile")
                        .header("Origin", CONFIGURED_ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header(
                                "Access-Control-Request-Headers",
                                "Authorization")));
    }

    @Test
    void rejectsAuthenticatedAndAnonymousMutationsWithoutCsrf()
            throws Exception {
        mvc.perform(patch("/api/v1/me/profile")
                        .with(user("ana@example.com").roles("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","phone":"+56912345678"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso denegado."))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(
                        "X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"));

        mvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana","email":"ana@example.com",
                                 "message":"Consulta","website":""}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso denegado."))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(
                        "X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"));
    }

    @Test
    void protectedResponsesCarryTheReviewedBrowserHeaders()
            throws Exception {
        mvc.perform(get("/api/v1/me/probe")
                        .with(user("ana@example.com").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(
                        "X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"));
    }

    @Test
    void protectedApisUseJson401And403WithoutChallengeOrHtml()
            throws Exception {
        mvc.perform(get("/api/v1/me/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(
                        "X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"));

        mvc.perform(get("/api/v1/admin/probe")
                        .with(user("ana@example.com").roles("CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(
                        "X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"));
    }

    @Test
    void unexpectedApiErrorsExposeOnlyTheClosedEnvelope()
            throws Exception {
        mvc.perform(get("/api/v1/me/unexpected")
                        .with(user("ana@example.com").roles("CLIENT")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(content().string(not(containsString(
                        IllegalStateException.class.getName()))))
                .andExpect(content().string(not(containsString(
                        SQLException.class.getName()))))
                .andExpect(content().string(not(containsString(
                        "at com.amidog"))))
                .andExpect(content().string(not(containsString(
                        "select password_hash from user_account"))))
                .andExpect(content().string(not(containsString(
                        "jdbc:postgresql://db.internal/amidog"))))
                .andExpect(content().string(not(containsString(
                        "db-password=production-secret"))));
    }

    @Test
    void bindsSeparateEmailLinkBaseAndBrowserOriginProperties() {
        productionProperties().run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            AmidogProperties properties =
                    context.getBean(AmidogProperties.class);

            assertThat(properties.frontendBaseUrl()).isEqualTo(
                    URI.create("https://links.amidog.cl/application/"));
            assertThat(properties.frontendOrigin()).isEqualTo(
                    URI.create(CONFIGURED_ORIGIN));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*",
            "https://*.amidog.cl",
            "https://app.amidog.cl:",
            "https://app.amidog.cl:0",
            "https://app.amidog.cl:65536",
            "https://user:password@app.amidog.cl",
            "https://@app.amidog.cl",
            "https://%75ser@app.amidog.cl",
            "https://app.amidog.cl/",
            "https://app.amidog.cl/path",
            "https://app.amidog.cl/%2e",
            "https://app.amidog.cl%2Fadmin",
            "https://app.amidog.cl?",
            "https://app.amidog.cl?tenant=clinic",
            "https://app.amidog.cl#",
            "https://app.amidog.cl#section",
            "https:app.amidog.cl",
            "https://app.amidog.cl,https://evil.invalid",
            "https://app.amidog.cl https://evil.invalid",
            "not-an-origin",
            "ftp://app.amidog.cl"
    })
    void unsafeOrAmbiguousOriginsFailClosedAtStartup(String origin) {
        productionProperties()
                .withPropertyValues("FRONTEND_ORIGIN=" + origin)
                .run(context ->
                        assertThat(context.getStartupFailure()).isNotNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://app.amidog.cl",
            "http://localhost:5173",
            "https://app.amidog.cl:8443",
            "http://127.0.0.1",
            "https://[2001:db8::1]",
            "https://[2001:db8::1]:8443"
    })
    void exactHttpOriginsIncludingBracketedIpv6Bind(String origin) {
        productionProperties()
                .withPropertyValues("FRONTEND_ORIGIN=" + origin)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(AmidogProperties.class)
                            .frontendOrigin()).isEqualTo(URI.create(origin));
                });
    }

    @Test
    void productionSessionAndCookieSettingsBindWithASecureOverride() {
        productionProperties().run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBean(ServerProperties.class)
                    .getServlet()
                    .getSession()
                    .getCookie()
                    .getSecure()).isFalse();
        });

        productionProperties()
                .withPropertyValues("SESSION_COOKIE_SECURE=true")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    ServerProperties server =
                            context.getBean(ServerProperties.class);
                    Cookie cookie = server.getServlet()
                            .getSession()
                            .getCookie();
                    JdbcSessionProperties jdbcSession =
                            context.getBean(JdbcSessionProperties.class);

                    assertThat(server.getServlet().getSession().getTimeout())
                            .isEqualTo(Duration.ofHours(8));
                    assertThat(cookie.getHttpOnly()).isTrue();
                    assertThat(cookie.getSecure()).isTrue();
                    assertThat(cookie.getSameSite())
                            .isEqualTo(Cookie.SameSite.LAX);
                    assertThat(jdbcSession.getInitializeSchema())
                            .isEqualTo(DatabaseInitializationMode.NEVER);
                });
    }

    private static ApplicationContextRunner productionProperties() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    YamlPropertySourceLoader loader =
                            new YamlPropertySourceLoader();
                    try {
                        loader.load(
                                        "production-application",
                                        new FileSystemResource(
                                                "src/main/resources/application.yaml"))
                                .forEach(context.getEnvironment()
                                        .getPropertySources()::addLast);
                    } catch (IOException exception) {
                        throw new IllegalStateException(
                                "Could not load production application.yaml",
                                exception);
                    }
                })
                .withPropertyValues(
                        "FRONTEND_BASE_URL=https://links.amidog.cl/application/",
                        "FRONTEND_ORIGIN=https://app.amidog.cl")
                .withUserConfiguration(BoundPropertiesConfiguration.class);
    }

    private static void assertClosedCorsRejection(ResultActions result)
            throws Exception {
        result
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(
                        "application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Acceso denegado."))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(header().doesNotExist(
                        "Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist(
                        "Access-Control-Allow-Credentials"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(
                        "X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class SecurityTestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestDependencies {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("security-test")
                            .password("{noop}unused")
                            .roles("CLIENT")
                            .build());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            AmidogProperties.class,
            ServerProperties.class,
            JdbcSessionProperties.class
    })
    static class BoundPropertiesConfiguration {
    }

    @RestController
    @RequestMapping("/api/v1")
    static class SecurityProbeController {
        @GetMapping("/me/probe")
        String client() {
            return "client";
        }

        @PatchMapping("/me/profile")
        void updateProfile() {
        }

        @GetMapping("/admin/probe")
        String admin() {
            return "admin";
        }

        @PostMapping("/contact")
        void contact() {
        }

        @GetMapping("/me/unexpected")
        void unexpected() {
            throw new IllegalStateException(
                    "db-password=production-secret; "
                            + "jdbc:postgresql://db.internal/amidog",
                    new SQLException(
                            "select password_hash from user_account"));
        }
    }
}
