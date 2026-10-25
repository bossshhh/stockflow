package com.stockflow.inventory;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.stockflow.AbstractIntegrationTest;
import com.stockflow.order.OrderService;
import com.stockflow.order.TestAddresses;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;

/** The public catalog, browsed anonymously (no token on any request here unless stated). */
class CatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String CATALOG = "/api/v1/catalog";

    @Autowired
    private OrderService orderService;

    private long reservedOut;
    private long retired;

    /**
     * APPLE 3.00 (in stock), BANANA 1.00 (0 on hand), CHERRY 2.00 (2 on hand, both reserved),
     * RETIRED (deactivated), "50% OFF_SALE" 9.00 (in stock).
     */
    @BeforeEach
    void products() throws Exception {
        productWithStock("APPLE", "3.00", 5);
        productWithStock("BANANA", "1.00", 0);
        reservedOut = productWithStock("CHERRY", "2.00", 2);
        retired = productWithStock("RETIRED", "4.00", 9);
        productWithStock("PROMO", "9.00", 1);
        jdbcTemplate.update("UPDATE products SET name = '50% OFF_SALE' WHERE sku = 'PROMO'");
        mockMvc.perform(delete("/api/v1/products/" + retired).with(staff()));
        long customer = createCustomer("ada@example.com");
        orderService.create(asCustomer(customer), new CreateOrderRequest(List.of(new OrderItemRequest(reservedOut, 2)), TestAddresses.ADDRESS));
    }

    @Test
    void anonymousCanBrowseActiveProductsByName() throws Exception {
        mockMvc.perform(get(CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].sku", contains("PROMO", "APPLE", "BANANA", "CHERRY")))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void inStockMeansAvailableNotOnHand() throws Exception {
        mockMvc.perform(get(CATALOG).param("sort", "price,asc"))
                .andExpect(jsonPath("$.content[*].sku", contains("BANANA", "CHERRY", "APPLE", "PROMO")))
                .andExpect(jsonPath("$.content[*].inStock", contains(false, false, true, true)));
    }

    @Test
    void stockCountsAreNeverExposed() throws Exception {
        mockMvc.perform(get(CATALOG))
                .andExpect(jsonPath("$.content[0].onHand").doesNotExist())
                .andExpect(jsonPath("$.content[0].reserved").doesNotExist())
                .andExpect(jsonPath("$.content[0].available").doesNotExist());
    }

    @Test
    void searchIsCaseInsensitiveSubstring() throws Exception {
        mockMvc.perform(get(CATALOG).param("q", "aPpL"))
                .andExpect(jsonPath("$.content[*].sku", contains("APPLE")));
    }

    @Test
    void wildcardCharactersInTheSearchAreLiteral() throws Exception {
        // Unescaped, "%" would match every product and "_" any single character.
        mockMvc.perform(get(CATALOG).param("q", "%"))
                .andExpect(jsonPath("$.content[*].sku", contains("PROMO")));
        mockMvc.perform(get(CATALOG).param("q", "o_f"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deactivatedProductIsNotFound() throws Exception {
        mockMvc.perform(get(CATALOG + "/" + retired))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(get(CATALOG + "/" + reservedOut))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inStock").value(false));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        mockMvc.perform(get(CATALOG).param("sort", "sku")).andExpect(status().isBadRequest());
        mockMvc.perform(get(CATALOG).param("q", "x".repeat(101))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rootRedirectsToSwagger() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/swagger-ui.html"));
    }
}
