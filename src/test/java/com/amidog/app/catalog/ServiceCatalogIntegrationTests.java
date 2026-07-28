package com.amidog.app.catalog;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.auth.AccountType;
import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ServiceCatalogIntegrationTests extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ServiceOfferingRepository services;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from reservation_items").update();
        jdbc.sql("delete from reservation_events").update();
        jdbc.sql("delete from notifications").update();
        jdbc.sql("delete from reservations").update();
        services.deleteAll();
    }

    @Test
    void publicCatalogOnlyShowsActiveDtosInDeterministicOrder() throws Exception {
        insert("vacuna", "Vacuna", true, 2);
        insert("consulta-b", "Consulta B", true, 1);
        insert("consulta-a", "Consulta A", true, 1);
        insert("oculto", "Oculto", false, 0);

        mvc.perform(get("/api/v1/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("consulta-a"))
                .andExpect(jsonPath("$[1].code").value("consulta-b"))
                .andExpect(jsonPath("$[2].code").value("vacuna"))
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].version").doesNotExist());
    }

    @Test
    void adminCanNormalizeCreateUpdateReactivateAndArchive() throws Exception {
        mvc.perform(post("/api/v1/admin/services")
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":" Atención DENTÁL ","name":"  Dental  ",
                                 "description":"  Evaluación  ","displayOrder":3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.code").value("atencion-dental"))
                .andExpect(jsonPath("$.name").value("Dental"))
                .andExpect(jsonPath("$.description").value("Evaluación"));

        long id = services.findAll().getFirst().getId();
        mvc.perform(delete("/api/v1/admin/services/{id}", id)
                        .with(user(admin())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/services/{id}", id)
                        .with(user(admin())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/services")).andExpect(jsonPath("$.length()").value(0));
        assertThat(services.findById(id).orElseThrow().isActive()).isFalse();

        mvc.perform(patch("/api/v1/admin/services/{id}", id)
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dental nueva","description":" ","displayOrder":0,"active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("atencion-dental"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void normalizedDuplicateHasStableConflictAndAdminMutationsNeedRoleAndCsrf() throws Exception {
        insert("consulta-general", "Consulta", true, 0);
        String duplicate = """
                {"code":"Consulta GÉNERAL","name":"Otra","displayOrder":0}
                """;

        mvc.perform(post("/api/v1/admin/services")
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERVICE_CODE_EXISTS"))
                .andExpect(jsonPath("$.message").value("Ya existe un servicio con ese código."));

        mvc.perform(post("/api/v1/admin/services")
                        .with(user(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/services").with(user(client())))
                .andExpect(status().isForbidden());
    }

    @Test
    void rawCodeOverSixtyCharactersFailsValidationAndRealConstraintMetadataIsExact() throws Exception {
        mvc.perform(post("/api/v1/admin/services")
                        .with(user(admin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"Consulta","displayOrder":0}
                                """.formatted("a".repeat(30) + " " + "b".repeat(30))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.code").exists());

        insert("codigo-unico", "Consulta", true, 0);
        try {
            jdbc.sql("""
                    insert into services(code, name)
                    values ('codigo-unico', 'Duplicado')
                    """).update();
            throw new AssertionError("Expected PostgreSQL unique violation");
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            assertThat(ServiceCatalogService.isServiceCodeUniqueViolation(exception)).isTrue();
        }
    }

    private void insert(String code, String name, boolean active, int order) {
        jdbc.sql("""
                insert into services(code, name, active, display_order)
                values (:code, :name, :active, :displayOrder)
                """)
                .param("code", code).param("name", name)
                .param("active", active).param("displayOrder", order).update();
    }

    private AccountPrincipal admin() {
        return new AccountPrincipal(90L, null, "admin@example.com", "Admin",
                AccountType.ADMIN, "{noop}password", true, true);
    }

    private AccountPrincipal client() {
        return new AccountPrincipal(91L, 91L, "client@example.com", "Cliente",
                AccountType.CLIENT, "{noop}password", true, true);
    }
}
