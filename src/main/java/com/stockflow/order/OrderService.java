package com.stockflow.order;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockflow.inventory.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.product.ProductService;
import com.stockflow.product.dto.ProductResponse;

/**
 * Order use cases. Each public method is ONE transaction covering the order row and every stock change,
 * so a failure anywhere (e.g. the third item is out of stock) rolls everything back.
 *
 * <p>Lock ordering, which is what prevents deadlocks: the order row first (confirm/cancel), then stock rows
 * in ascending product id. Two transactions that lock the same rows in the same order cannot each hold a
 * lock the other is waiting for.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Comparator<OrderItem> LOCK_ORDER = Comparator.comparing(OrderItem::getProductId);

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final OrderProperties orderProperties;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, ProductService productService,
                        InventoryService inventoryService, OrderProperties orderProperties, Clock clock) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.orderProperties = orderProperties;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        // TreeMap: duplicates merged AND iterated in ascending product id (the lock order).
        SortedMap<Long, Integer> quantities = mergeQuantities(request.items());
        Map<Long, ProductResponse> products = productService.getByIds(quantities.keySet()).stream()
                .collect(Collectors.toMap(ProductResponse::id, Function.identity()));

        CustomerOrder order = new CustomerOrder(request.customerName().strip(), request.customerEmail().strip(),
                clock.instant());
        quantities.forEach((productId, quantity) -> {
            ProductResponse product = products.get(productId);
            if (!product.active()) {
                throw new ProductUnavailableException(product.sku());
            }
            order.addItem(productId, product.sku(), product.name(), product.price(), quantity);
        });

        // Insert first: stock movements reference the order id.
        CustomerOrder saved = orderRepository.saveAndFlush(order);
        quantities.forEach((productId, quantity) -> inventoryService.reserve(productId, quantity, saved.getId()));

        log.info("Created order id={} items={} total={}", saved.getId(), quantities.size(), saved.getTotalAmount());
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(long id) {
        return orderRepository.findWithItemsById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional
    public OrderResponse confirm(long id) {
        CustomerOrder order = lockOrThrow(id);
        order.confirm(clock.instant());
        itemsInLockOrder(order).forEach(item ->
                inventoryService.fulfill(item.getProductId(), item.getQuantity(), order.getId()));
        log.info("Confirmed order id={}", order.getId());
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(long id) {
        CustomerOrder order = lockOrThrow(id);
        OrderStatus previous = order.getStatus();
        Instant now = clock.instant();
        order.cancel(now, orderProperties.confirmedCancelWindow());

        // PENDING: units were only set aside. CONFIRMED: units had left on-hand stock and come back.
        for (OrderItem item : itemsInLockOrder(order)) {
            if (previous == OrderStatus.PENDING) {
                inventoryService.release(item.getProductId(), item.getQuantity(), order.getId());
            } else {
                inventoryService.restock(item.getProductId(), item.getQuantity(), order.getId());
            }
        }
        log.info("Cancelled order id={} previousStatus={}", order.getId(), previous);
        return OrderResponse.from(order);
    }

    private CustomerOrder lockOrThrow(long id) {
        return orderRepository.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private static List<OrderItem> itemsInLockOrder(CustomerOrder order) {
        return order.getItems().stream().sorted(LOCK_ORDER).toList();
    }

    private static SortedMap<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        SortedMap<Long, Integer> merged = new TreeMap<>();
        for (OrderItemRequest item : items) {
            merged.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return merged;
    }
}
