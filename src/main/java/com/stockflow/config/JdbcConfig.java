package com.stockflow.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Our own JdbcTemplate (Spring Boot's backs off), only to plug in lock-timeout translation. JdbcClient and
 * NamedParameterJdbcTemplate are still auto-configured on top of this one.
 */
@Configuration(proxyBeanMethods = false)
class JdbcConfig {

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setExceptionTranslator(new LockTimeoutAwareExceptionTranslator(jdbcTemplate.getExceptionTranslator()));
        return jdbcTemplate;
    }
}
