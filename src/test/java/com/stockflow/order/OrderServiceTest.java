package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.stockflow.inventory.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.product.ProductService;
import com.stockflow.product.dto.ProductResponse;
import com.stockflow.user.CurrentUser;
import com.stockflow.user.Role;
import com.stockflow.user.UserService;
import com.stockflow.user.dto.UserResponse;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final long ORDER_ID = 100L;
    private static final long CUSTOMER_ID = 7L;
    private static final CurrentUser CUSTOMER = new CurrentUser(CUSTOMER_ID, Role.CUSTOMER);
    private static final CurrentUser OTHER_CUSTOMER = new CurrentUser(8L, Role.CUSTOMER);
    private static final CurrentUser STAFF = new CurrentUser(99L, Role.STAFF);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderIdempotencyKeyRepository idempotencyKeys;

    @Mock
    private ProductService productService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private UserService userService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, idempotencyKeys, productService, inventoryService, userService,
                new OrderProperties(Duration.ofMinutes(5), Duration.ofHours(24), Duration.ofHours(48)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createMergesDuplicatesAndReservesInAscendingProductIdOrder() {
        stubAccount();
        when(productService.getByIds(Set.of(3L, 9L))).thenReturn(List.of(product(9L, true), product(3L, true)));
        when(orderRepository.saveAndFlush(any(CustomerOrder.class))).thenAnswer(call -> withId(call.getArgument(0)));

        OrderResponse response = orderService.create(CUSTOMER, new CreateOrderRequest(List.of(
                new OrderItemRequest(9L, 1), new OrderItemRequest(3L, 2), new OrderItemRequest(9L, 4)), TestAddresses.ADDRESS));

        assertThat(response.items()).extracting(item -> item.productId()).containsExactly(3L, 9L);
        assertThat(response.items()).extracting(item -> item.quantity()).containsExactly(2, 5);
        InOrder reservations = inOrder(inventoryService);
        reservations.verify(inventoryService).reserve(3L, 2, ORDER_ID);
        reservations.verify(inventoryService).reserve(9L, 5, ORDER_ID);
    }

    @Test
    void createSnapshotsPriceAndComputesTotal() {
        stubAccount();
        when(productService.getByIds(Set.of(3L))).thenReturn(List.of(product(3L, true)));
        when(orderRepository.saveAndFlush(any(CustomerOrder.class))).thenAnswer(call -> withId(call.getArgument(0)));

        OrderResponse response = orderService.create(CUSTOMER, new CreateOrderRequest(
                List.of(new OrderItemRequest(3L, 3)), TestAddresses.ADDRESS));

        assertThat(response.items().getFirst().unitPrice()).isEqualByComparingTo("2.50");
        assertThat(response.totalAmount()).isEqualByComparingTo("7.50");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.customerName()).isEqualTo("Ada");
    }

    @Test
    void createRejectsInactiveProductBeforeTouchingStock() {
        stubAccount();
        when(productService.getByIds(Set.of(3L))).thenReturn(List.of(product(3L, false)));

        assertThatThrownBy(() -> orderService.create(CUSTOMER, new CreateOrderRequest(
                List.of(new OrderItemRequest(3L, 1)), TestAddresses.ADDRESS)))
                .isInstanceOf(ProductUnavailableException.class);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(inventoryService, never()).reserve(anyLong(), anyInt(), anyLong());
    }

    @Test
    void cancelPendingReleasesReservation() {
        CustomerOrder order = orderWithItem();
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancel(CUSTOMER, ORDER_ID);

        verify(inventoryService).release(3L, 2, ORDER_ID);
        verify(inventoryService, never()).restock(anyLong(), anyInt(), anyLong());
    }

    @Test
    void cancelConfirmedRestocks() {
        CustomerOrder order = orderWithItem();
        order.confirm(NOW, 99L);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancel(CUSTOMER, ORDER_ID);

        verify(inventoryService).restock(3L, 2, ORDER_ID);
        verify(inventoryService, never()).release(anyLong(), anyInt(), anyLong());
    }

    @Test
    void confirmRejectedStateTouchesNoStock() {
        CustomerOrder order = orderWithItem();
        order.cancel(NOW, Duration.ofMinutes(5), 99L);
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirm(STAFF, ORDER_ID)).isInstanceOf(InvalidOrderStateException.class);
        verify(inventoryService, never()).fulfill(anyLong(), anyInt(), anyLong());
    }

    @Test
    void unknownOrderThrowsNotFound() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirm(STAFF, ORDER_ID)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void customerCannotSeeOrCancelSomeoneElsesOrder() {
        CustomerOrder order = orderWithItem();
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getById(OTHER_CUSTOMER, ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> orderService.cancel(OTHER_CUSTOMER, ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class);
        verify(inventoryService, never()).release(anyLong(), anyInt(), anyLong());
    }

    @Test
    void staffCanSeeAndCancelAnyOrder() {
        CustomerOrder order = orderWithItem();
        when(orderRepository.findWithItemsById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThat(orderService.getById(STAFF, ORDER_ID).id()).isEqualTo(ORDER_ID);
        orderService.cancel(STAFF, ORDER_ID);

        verify(inventoryService).release(3L, 2, ORDER_ID);
    }

    private void stubAccount() {
        when(userService.getById(CUSTOMER_ID))
                .thenReturn(new UserResponse(CUSTOMER_ID, "ada@example.com", "Ada", Role.CUSTOMER, NOW));
    }

    private static ProductResponse product(long id, boolean active) {
        return new ProductResponse(id, "SKU-" + id, "Product " + id, null, new BigDecimal("2.50"), active, NOW, NOW);
    }

    private static CustomerOrder orderWithItem() {
        CustomerOrder order = new CustomerOrder(CUSTOMER_ID, "Ada", "ada@example.com", TestAddresses.SHIPPING_ADDRESS, NOW);
        order.addItem(3L, "SKU-3", "Product 3", new BigDecimal("2.50"), 2);
        return withId(order);
    }

    /** Simulates the database assigning the identity column on insert. */
    private static CustomerOrder withId(CustomerOrder order) {
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }
}
