package com.stockflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.stockflow.inventory.InventoryService;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.product.ProductService;
import com.stockflow.product.dto.CreateProductRequest;
import com.stockflow.user.CurrentUser;
import com.stockflow.user.Role;
import com.stockflow.user.UserService;
import com.stockflow.user.dto.RegisterRequest;

/**
 * Base for every integration test. All subclasses share one Spring context and therefore one Postgres
 * container (Spring caches contexts with identical configuration), and each test starts from empty tables
 * and the same clock time.
 *
 * <p>Requests are anonymous unless a test chooses an actor: {@code mvcAs(staff())} for a whole class, or
 * {@code .with(customer(id))} for one request. Actors are injected as already-validated JWTs; the real
 * login and token validation path is covered separately by AuthIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public abstract class AbstractIntegrationTest {

    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";

    /** Staff/admin actions never look their account up, so these ids need no database row. */
    protected static final long STAFF_ID = 900_001L;
    protected static final long ADMIN_ID = 900_002L;
    protected static final long WAREHOUSE_ID = 900_003L;
    protected static final CurrentUser STAFF_ACTOR = new CurrentUser(STAFF_ID, Role.STAFF);
    protected static final String TEST_PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private UserService userService;

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

    // ---- actors -------------------------------------------------------------------------------------

    protected static RequestPostProcessor staff() {
        return actor(STAFF_ID, Role.STAFF);
    }

    protected static RequestPostProcessor warehouse() {
        return actor(WAREHOUSE_ID, Role.WAREHOUSE);
    }

    protected static RequestPostProcessor admin() {
        return actor(ADMIN_ID, Role.ADMIN);
    }

    protected static RequestPostProcessor customer(long userId) {
        return actor(userId, Role.CUSTOMER);
    }

    protected static CurrentUser asCustomer(long userId) {
        return new CurrentUser(userId, Role.CUSTOMER);
    }

    /** A MockMvc whose every request is made by {@code actor} (requests can still override it). */
    protected MockMvc mvcAs(RequestPostProcessor actor) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(actor))
                .build();
    }

    private static RequestPostProcessor actor(long userId, Role role) {
        return jwt()
                .jwt(token -> token.subject(String.valueOf(userId)).claim(CurrentUser.ROLES_CLAIM, List.of(role.name())))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // ---- fixtures -----------------------------------------------------------------------------------

    /** A registered CUSTOMER account. Returns its id. */
    protected long createCustomer(String email) {
        return userService.register(new RegisterRequest(email, TEST_PASSWORD, "Customer " + email)).id();
    }

    /** A product with {@code stock} units on hand. Returns its id. */
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
