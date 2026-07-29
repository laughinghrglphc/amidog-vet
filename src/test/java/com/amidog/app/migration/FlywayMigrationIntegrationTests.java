package com.amidog.app.migration;

import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
class FlywayMigrationIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DataSource dataSource;

    @Test
    void createsAccountClientPetTokenAndSessionTables() {
        Integer count = jdbc.sql("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'users', 'user_external_identities', 'clients', 'pets',
                    'email_verification_tokens', 'password_reset_tokens',
                    'spring_session', 'spring_session_attributes'
                  )
                """).query(Integer.class).single();

        assertThat(count).isEqualTo(8);
    }

    @Test
    void v5AndV6SafelyTerminateUnlinkablePreexistingJobsAndAddExactHashInvariant() {
        String schema = "upgrade_v5_test";
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target("4")
                .load()
                .migrate();
        JdbcClient isolated = JdbcClient.create(dataSource);
        Long userId = isolated.sql("""
                insert into upgrade_v5_test.users(
                    email_normalized, password_hash, account_type, enabled)
                values ('upgrade@example.com', 'hash', 'CLIENT', true)
                returning id
                """).query(Long.class).single();
        Long jobId = isolated.sql("""
                insert into upgrade_v5_test.email_delivery_jobs(
                    user_id, delivery_type, state, attempts, next_attempt_at,
                    lease_until, delivery_fence)
                values (:userId, 'PASSWORD_RESET', 'PROCESSING', 0, now(),
                    now() + interval '1 minute', :fence)
                returning id
                """)
                .param("userId", userId)
                .param("fence", "f".repeat(64))
                .query(Long.class).single();

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(isolated.sql("""
                select state from upgrade_v5_test.email_delivery_jobs where id=:id
                """).param("id", jobId).query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(isolated.sql("""
                select token_hash from upgrade_v5_test.email_delivery_jobs where id=:id
                """).param("id", jobId).query(String.class).optional()).isEmpty();
        assertThat(isolated.sql("""
                select count(*) from upgrade_v5_test.flyway_schema_history
                where version in ('1','2','3','4','5','6') and success
                """).query(Integer.class).single()).isEqualTo(6);
        assertThat(isolated.sql("""
                select count(*) from pg_indexes
                where schemaname=:schema and indexname='email_delivery_jobs_token_hash_uq'
                """).param("schema", schema).query(Integer.class).single()).isOne();
        assertThat(isolated.sql("""
                select count(*) from information_schema.table_constraints
                where table_schema=:schema
                  and table_name='email_delivery_jobs'
                  and constraint_name='email_delivery_jobs_token_hash_ck'
                  and constraint_type='CHECK'
                """).param("schema", schema).query(Integer.class).single()).isOne();
    }

    @Test
    void v6TerminalizesInvalidV5RowsBeforeEnforcingTheConstraint() {
        String schema = "upgrade_v6_test";
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target("5")
                .load()
                .migrate();
        JdbcClient isolated = JdbcClient.create(dataSource);
        Long userId = isolated.sql("""
                insert into upgrade_v6_test.users(
                    email_normalized, password_hash, account_type, enabled)
                values ('upgrade-v6@example.com', 'hash', 'CLIENT', true)
                returning id
                """).query(Long.class).single();
        isolated.sql("""
                insert into upgrade_v6_test.email_delivery_jobs(
                    user_id, delivery_type, state, attempts, next_attempt_at,
                    lease_until, delivery_fence, token_hash)
                values (:userId, 'PASSWORD_RESET', 'PENDING', 0, now(), null,
                    :fence, :invalidHash)
                """)
                .param("userId", userId)
                .param("fence", "v6-invalid-active")
                .param("invalidHash", "G".repeat(64))
                .update();

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(isolated.sql("""
                select state from upgrade_v6_test.email_delivery_jobs
                """).query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(isolated.sql("""
                select token_hash from upgrade_v6_test.email_delivery_jobs
                """).query(String.class).optional()).isEmpty();
    }
}
