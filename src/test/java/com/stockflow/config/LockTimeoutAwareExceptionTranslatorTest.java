package com.stockflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator;

class LockTimeoutAwareExceptionTranslatorTest {

    private final LockTimeoutAwareExceptionTranslator translator =
            new LockTimeoutAwareExceptionTranslator(new SQLStateSQLExceptionTranslator());

    @Test
    void postgresLockTimeoutBecomesCannotAcquireLock() {
        SQLException lockTimeout = new SQLException("canceling statement due to lock timeout", "55P03");

        assertThat(translator.translate("claim key", "INSERT ...", lockTimeout))
                .isInstanceOf(CannotAcquireLockException.class)
                .hasCause(lockTimeout);
    }

    @Test
    void everythingElseIsLeftToTheDefaultTranslation() {
        SQLException duplicate = new SQLException("duplicate key", "23505");

        assertThat(translator.translate("insert", "INSERT ...", duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
