package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.stockflow.AbstractIntegrationTest;
import com.stockflow.common.web.ApiHeaders;

/**
 * Every new order carries a German shipping address, stored as a snapshot on the order (step 8.7a).
 * The limits are DHL's own, so an address we accept is never rejected by DHL at ship time.
 */
class ShippingAddressIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final String KEY = "checkout-addr-1";

    private MockMvc customerMvc;
    private long apple;

    @BeforeEach
    void setUp() {
        customerMvc = mvcAs(customer(createCustomer("ada@example.com")));
        apple = productWithStock("APPLE", "1.00", 10);
    }

    @Test
    void placingAnOrderStoresTheAddressAndReturnsIt() throws Exception {
        long id = orderId(place(body(address("Berlin", "10115")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingAddress.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.shippingAddress.street").value("Musterstraße"))
                .andExpect(jsonPath("$.shippingAddress.houseNumber").value("12a"))
                .andExpect(jsonPath("$.shippingAddress.postcode").value("10115"))
                .andExpect(jsonPath("$.shippingAddress.city").value("Berlin"))
                .andExpect(jsonPath("$.shippingAddress.country").value("DEU")));

        customerMvc.perform(get(ORDERS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress.postcode").value("10115"));
        assertThat(jdbcTemplate.queryForObject("SELECT ship_city FROM customer_orders WHERE id = ?", String.class, id))
                .isEqualTo("Berlin");
    }

    @Test
    void missingAddressIsRejected() throws Exception {
        place("""
                {"items": [{"productId": %d, "quantity": 1}]}""".formatted(apple))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("shippingAddress"));
        assertThat(orderCount()).isZero();
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("postcode", "1234"),
                Arguments.of("postcode", "123456"),
                Arguments.of("postcode", "12a45"),
                Arguments.of("postcode", "١٢٣٤٥"),
                Arguments.of("name", "   "),
                Arguments.of("name", "Ada\nLovelace"),
                Arguments.of("name", "Ada\u0085Lovelace"),
                Arguments.of("city", "Ber\u2028lin"),
                Arguments.of("name", "\u00A0"),
                Arguments.of("city", "\uFEFF"),
                Arguments.of("name", "Ada\u202ELovelace"),
                Arguments.of("street", "s".repeat(51)),
                Arguments.of("houseNumber", "1".repeat(11)),
                Arguments.of("city", "c".repeat(41)));
    }

    @ParameterizedTest(name = "{0} = \"{1}\"")
    @MethodSource("invalidFields")
    void invalidAddressFieldsAreRejected(String field, String value) throws Exception {
        String address = withField(field, value);
        place(body(address))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("shippingAddress." + field));
        assertThat(orderCount()).isZero();
    }

    @Test
    void longestAllowedValuesAreAccepted() throws Exception {
        String address = """
                {"name": "%s", "street": "%s", "houseNumber": "%s", "postcode": "99998", "city": "%s"}"""
                .formatted("n".repeat(50), "s".repeat(50), "1".repeat(10), "c".repeat(40));
        place(body(address)).andExpect(status().isCreated());
    }

    @Test
    void clientCountryIsIgnored() throws Exception {
        String address = """
                {"name": "Ada Lovelace", "street": "Musterstraße", "houseNumber": "12a", "postcode": "10115",
                 "city": "Berlin", "country": "USA"}""";
        place(body(address))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingAddress.country").value("DEU"));
    }

    @Test
    void whitespaceIsTrimmed() throws Exception {
        place(body(address("  Berlin  ", "10115")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingAddress.city").value("Berlin"));
    }

    @Test
    void sameKeyWithADifferentAddressIsRejected() throws Exception {
        placeWithKey(body(address("Berlin", "10115"))).andExpect(status().isCreated());

        placeWithKey(body(address("Berlin", "20095")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void sameKeyWithTheSameAddressPlusWhitespaceReplays() throws Exception {
        long id = orderId(placeWithKey(body(address("Berlin", "10115"))).andExpect(status().isCreated()));

        placeWithKey(body(address(" Berlin ", "10115")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void orderPlacedBeforeAddressesHasNoAddress() throws Exception {
        long id = orderId(place(body(address("Berlin", "10115"))));
        jdbcTemplate.update("""
                UPDATE customer_orders SET ship_name = NULL, ship_street = NULL, ship_house_number = NULL,
                    ship_postcode = NULL, ship_city = NULL, ship_country = NULL WHERE id = ?""", id);

        customerMvc.perform(get(ORDERS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress").doesNotExist());
    }

    @Test
    void partialAddressIsRefusedByTheDatabase() throws Exception {
        long id = orderId(place(body(address("Berlin", "10115"))));

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE customer_orders SET ship_city = NULL WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orderListDoesNotExposeAddresses() throws Exception {
        place(body(address("Berlin", "10115"))).andExpect(status().isCreated());

        mvcAs(staff()).perform(get(ORDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].shippingAddress").doesNotExist());
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private ResultActions place(String body) throws Exception {
        return customerMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions placeWithKey(String body) throws Exception {
        return customerMvc.perform(post(ORDERS).header(ApiHeaders.IDEMPOTENCY_KEY, KEY)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String body(String addressJson) {
        return """
                {"items": [{"productId": %d, "quantity": 1}], "shippingAddress": %s}""".formatted(apple, addressJson);
    }

    private static String address(String city, String postcode) {
        return """
                {"name": "Ada Lovelace", "street": "Musterstraße", "houseNumber": "12a", "postcode": "%s", "city": "%s"}"""
                .formatted(postcode, city);
    }

    /** The test address with one field replaced; the value is JSON-escaped (it may contain a newline). */
    private static String withField(String field, String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String base = """
                {"name": "Ada Lovelace", "street": "Musterstraße", "houseNumber": "12a", "postcode": "10115", "city": "Berlin"}""";
        return base.replaceFirst("\"" + field + "\": \"[^\"]*\"",
                Matcher.quoteReplacement("\"" + field + "\": \"" + escaped + "\""));
    }

    private static long orderId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private int orderCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM customer_orders", Integer.class);
        return count == null ? 0 : count;
    }
}
