package com.stockflow;

import org.junit.jupiter.api.Test;

class StockFlowApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Passes only if the app starts: config binds, Flyway runs, and Hibernate validates the schema.
    }
}
