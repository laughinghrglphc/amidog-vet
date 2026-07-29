package com.amidog.app.migration;

import com.amidog.app.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootFlywayStartupIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Test
    void runsCheckedInMigrationsBeforeJpaValidationAndManagesSchemaHistory() {
        assertThat(flyway.info().current().getVersion().getVersion())
                .isEqualTo("9");
        assertThat(jdbc.sql("""
                select count(*)
                from flyway_schema_history
                where version between '1' and '9' and success
                """).query(Integer.class).single()).isEqualTo(9);
        assertThat(jdbc.sql("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'availability_blocks'
                """).query(Integer.class).single()).isOne();
    }
}
