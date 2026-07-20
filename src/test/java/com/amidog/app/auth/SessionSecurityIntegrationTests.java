package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.support.PostgresIntegrationTest;
import com.amidog.app.support.RateLimitTestSupport;
import com.amidog.app.common.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(SessionSecurityIntegrationTests.ProbeConfiguration.class)
class SessionSecurityIntegrationTests extends PostgresIntegrationTest {

    private static final String GENERIC_LOGIN_FAILURE =
            "Correo o contraseña incorrectos, o cuenta aún no verificada.";

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired ClientRepository clients;
    @Autowired JdbcTemplate jdbc;
    @Autowired RateLimitService rateLimits;
    @Autowired @SuppressWarnings("rawtypes") SessionRepository sessions;

    @BeforeEach
    void cleanDatabase() {
        RateLimitTestSupport.reset(rateLimits);
        jdbc.update("delete from spring_session_attributes");
        jdbc.update("delete from spring_session");
        clients.deleteAll();
        users.deleteAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifiedClientCanLoginReuseTheDatabaseSessionAndReadItsAccount() throws Exception {
        UserAccount user = createVerifiedClient("ana@example.com", "Correct-Horse-9!", "Ana Pérez");
        Session anonymousSession = (Session) sessions.createSession();
        anonymousSession.setAttribute("anonymous-marker", "present-before-login");
        sessions.save(anonymousSession);
        String anonymousSessionId = anonymousSession.getId();
        Cookie anonymousSessionCookie = new Cookie("SESSION", encodeSessionId(anonymousSessionId));

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .cookie(anonymousSessionCookie)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", " ANA@EXAMPLE.COM ")
                        .param("password", "Correct-Horse-9!"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.clientId").value(clients.findByUserId(user.getId()).orElseThrow().getId()))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.accountType").value("CLIENT"))
                .andReturn();

        Cookie sessionCookie = login.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        assertThat(decodeSessionId(sessionCookie.getValue())).isNotEqualTo(anonymousSessionId);
        assertThat(sessionCount()).isEqualTo(1);
        assertThat(anySessionAttributeContains(user.getPasswordHash())).isFalse();

        mvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.email").value("ana@example.com"));
        assertThat(sessionCount()).isEqualTo(1);
    }

    @Test
    void unknownUnverifiedAndWrongPasswordLoginsHaveTheSameGenericJsonFailure() throws Exception {
        createUnverifiedClient("unverified@example.com", "Correct-Horse-9!", "Sin verificar");
        createVerifiedClient("verified@example.com", "Correct-Horse-9!", "Verificada");

        assertGenericLoginFailure("unverified@example.com", "Correct-Horse-9!");
        assertGenericLoginFailure("verified@example.com", "wrong-password");
        assertGenericLoginFailure("unknown@example.com", "Correct-Horse-9!");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void csrfEndpointCreatesReadableTokenAndMutationWithoutItIsJsonForbidden() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getAttribute("HttpOnly")).isNull();

        mvc.perform(post("/api/v1/auth/login")
                        .param("username", "nobody@example.com")
                        .param("password", "password"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.errors").isEmpty());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"Correct-Horse-9!\",\"name\":\"Ana\",\"phone\":\"+56912345678\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void logoutDeletesTheDatabaseSessionCookiesAndInvalidatesTheOldCookie() throws Exception {
        createVerifiedClient("ana@example.com", "Correct-Horse-9!", "Ana Pérez");
        Cookie sessionCookie = login("ana@example.com", "Correct-Horse-9!");
        assertThat(sessionCount()).isEqualTo(1);

        MvcResult logout = mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(sessionCookie))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(logout.getResponse().getCookie("SESSION").getMaxAge()).isZero();
        assertThat(logout.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();
        assertThat(sessionCount()).isZero();
        mvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void authorizationUsesClientAndAdminRolesWithJson401And403Responses() throws Exception {
        createVerifiedClient("client@example.com", "Correct-Horse-9!", "Cliente");
        createAdmin("admin@example.com", "Correct-Horse-9!");

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(result -> assertThat(result.getResponse().getHeader("WWW-Authenticate")).isNull());

        Cookie clientSession = login("client@example.com", "Correct-Horse-9!");
        mvc.perform(get("/api/v1/me/probe").cookie(clientSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/probe").cookie(clientSession))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Cookie adminSession = login("admin@example.com", "Correct-Horse-9!");
        mvc.perform(get("/api/v1/admin/probe").cookie(adminSession)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/probe").cookie(adminSession))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void loginRateLimitAcceptsTenAuthenticationAttemptsThenReturnsUnified429() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.77");
                                return request;
                            })
                            .param("username", "unknown@example.com")
                            .param("password", "wrong-password"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.errors").isEmpty());
        }

        mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.77");
                            return request;
                        })
                        .param("username", "unknown@example.com")
                        .param("password", "wrong-password"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private void assertGenericLoginFailure(String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.message").value(GENERIC_LOGIN_FAILURE));
    }

    private UserAccount createVerifiedClient(String email, String password, String name) {
        UserAccount user = users.saveAndFlush(UserAccount.client(email, new BCryptPasswordEncoder(4).encode(password), Instant.now()));
        user.verify(Instant.now());
        user = users.saveAndFlush(user);
        clients.saveAndFlush(Client.create(user, name, "+56912345678", Instant.now()));
        return user;
    }

    private void createUnverifiedClient(String email, String password, String name) {
        UserAccount user = users.saveAndFlush(UserAccount.client(email, new BCryptPasswordEncoder(4).encode(password), Instant.now()));
        clients.saveAndFlush(Client.create(user, name, "+56912345678", Instant.now()));
    }

    private void createAdmin(String email, String password) {
        users.saveAndFlush(UserAccount.admin(email, new BCryptPasswordEncoder(4).encode(password), Instant.now()));
    }

    private int sessionCount() {
        Integer count = jdbc.queryForObject("select count(*) from spring_session", Integer.class);
        return count == null ? 0 : count;
    }

    private String encodeSessionId(String sessionId) {
        return Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeSessionId(String cookieValue) {
        return new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
    }

    private boolean anySessionAttributeContains(String value) {
        byte[] sequence = value.getBytes(StandardCharsets.UTF_8);
        return jdbc.query("select attribute_bytes from spring_session_attributes", (resultSet, rowNumber) ->
                        resultSet.getBytes("attribute_bytes"))
                .stream()
                .anyMatch(bytes -> indexOf(bytes, sequence) >= 0);
    }

    private int indexOf(byte[] bytes, byte[] sequence) {
        for (int offset = 0; offset <= bytes.length - sequence.length; offset++) {
            boolean match = true;
            for (int index = 0; index < sequence.length; index++) {
                if (bytes[offset + index] != sequence[index]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return offset;
            }
        }
        return -1;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        RoleProbeController roleProbeController() {
            return new RoleProbeController();
        }
    }

    @RestController
    static class RoleProbeController {
        @GetMapping("/api/v1/admin/probe")
        String admin() {
            return "admin";
        }

        @GetMapping("/api/v1/me/probe")
        String client() {
            return "client";
        }
    }
}
