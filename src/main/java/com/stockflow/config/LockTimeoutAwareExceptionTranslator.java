package com.stockflow.config;

import java.sql.SQLException;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLExceptionTranslator;

/**
 * Plain JDBC (JdbcTemplate / JdbcClient) leaves PostgreSQL's lock timeout, SQLState 55P03, "uncategorized",
 * so it would surface as a 500. Hibernate already maps it to a pessimistic-locking failure; this makes
 * JDBC do the same, so every lock timeout becomes 409 CONCURRENT_UPDATE wherever the query was written.
 */
public class LockTimeoutAwareExceptionTranslator implements SQLExceptionTranslator {

    /** PostgreSQL "lock_not_available": raised when lock_timeout expires (or NOWAIT cannot lock). */
    static final String LOCK_NOT_AVAILABLE = "55P03";

    private final SQLExceptionTranslator delegate;

    public LockTimeoutAwareExceptionTranslator(SQLExceptionTranslator delegate) {
        this.delegate = delegate;
    }

    @Override
    public DataAccessException translate(String task, String sql, SQLException ex) {
        if (LOCK_NOT_AVAILABLE.equals(ex.getSQLState())) {
            return new CannotAcquireLockException(task + ": " + ex.getMessage(), ex);
        }
        return delegate.translate(task, sql, ex);
    }
}
