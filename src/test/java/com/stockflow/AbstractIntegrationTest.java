package com.stockflow;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.stockflow.inventory.InventoryService;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.product.ProductService;
import com.stockflow.product.dto.CreateProductRequest;

/**
 * Base for every integration test. All subclasses share one Spring context and therefore one Postgres
 * container (Spring caches contexts with identical configuration), and each test starts from empty tables
 * and the same clock time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public abstract class AbstractIntegrationTest {

    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    /** Truncates every application table, so new tables are cleaned without touching this class. */
    @BeforeEach
    void cleanDatabase() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> ?",
                String.class, FLYWAY_HISTORY_TABLE);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
        clock.setInstant(TestClockConfiguration.START);
    }

    /** Test fixture: a product with {@code stock} units on hand. Returns its id. */
    protected long productWithStock(String sku, String price, int stock) {
        long id = productService.create(new CreateProductRequest(sku, "Product " + sku, null, new BigDecimal(price))).id();
        if (stock > 0) {
            inventoryService.adjust(id, new StockAdjustmentRequest(stock, "Test stock"));
        }
        return id;
    }

    protected int onHand(long productId) {
        return inventoryService.getStockLevel(productId).onHand();
    }

    protected int reserved(long productId) {
        return inventoryService.getStockLevel(productId).reserved();
    }
}
