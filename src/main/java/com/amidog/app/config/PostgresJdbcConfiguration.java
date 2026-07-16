package com.amidog.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.function.Consumer;

@Configuration(proxyBeanMethods = false)
class PostgresJdbcConfiguration {

    @Bean
    @Primary
    NamedParameterJdbcTemplate namedParameterJdbcTemplate(
            JdbcTemplate jdbcTemplate) {
        return new InstantAwareNamedParameterJdbcTemplate(jdbcTemplate);
    }

    private static final class InstantAwareNamedParameterJdbcTemplate
            extends NamedParameterJdbcTemplate {

        private InstantAwareNamedParameterJdbcTemplate(
                JdbcTemplate jdbcTemplate) {
            super(jdbcTemplate);
        }

        @Override
        protected PreparedStatementCreator getPreparedStatementCreator(
                String sql,
                SqlParameterSource parameters,
                Consumer<PreparedStatementCreatorFactory> customizer) {
            return super.getPreparedStatementCreator(
                    sql,
                    new InstantAwareParameterSource(parameters),
                    customizer);
        }
    }

    private record InstantAwareParameterSource(SqlParameterSource delegate)
            implements SqlParameterSource {

        @Override
        public boolean hasValue(String parameterName) {
            return delegate.hasValue(parameterName);
        }

        @Override
        public Object getValue(String parameterName) {
            return jdbcValue(delegate.getValue(parameterName));
        }

        @Override
        public int getSqlType(String parameterName) {
            return delegate.getSqlType(parameterName);
        }

        @Override
        public String getTypeName(String parameterName) {
            return delegate.getTypeName(parameterName);
        }

        @Override
        public String[] getParameterNames() {
            return delegate.getParameterNames();
        }
    }

    private static Object jdbcValue(Object value) {
        if (value instanceof Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (value instanceof SqlParameterValue parameterValue) {
            return new SqlParameterValue(
                    parameterValue,
                    jdbcValue(parameterValue.getValue()));
        }
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .map(PostgresJdbcConfiguration::jdbcValue)
                    .toList();
        }
        if (value instanceof Object[] values) {
            return java.util.Arrays.stream(values)
                    .map(PostgresJdbcConfiguration::jdbcValue)
                    .toArray();
        }
        return value;
    }
}
