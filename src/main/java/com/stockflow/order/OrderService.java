package com.stockflow.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockflow.common.exception.InvalidRequestParameterException;
import com.stockflow.common.web.SortValidation;
import com.stockflow.inventory.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;
import com.stockflow.order.dto.OrderListItemResponse;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.dto.OrderSummaryReport;
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
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt", "totalAmount", "status");

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

    /** Every filter is optional. Results are summaries without items (no N+1); default order is newest first. */
    @Transactional(readOnly = true)
    public Page<OrderListItemResponse> search(OrderSearchCriteria criteria, Pageable pageable) {
        requireValidRange(criteria.createdFrom(), criteria.createdTo());
        SortValidation.requireAllowed(pageable.getSort(), SORTABLE_FIELDS);
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return orderRepository.findAll(OrderSpecifications.matching(criteria), effective)
                .map(OrderListItemResponse::from);
    }

    /** Report for orders created in [from, to). */
    @Transactional(readOnly = true)
    public OrderSummaryReport summarize(Instant from, Instant to) {
        requireValidRange(from, to);
        Map<OrderStatus, Long> countsByStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            countsByStatus.put(status, 0L);
        }
        BigDecimal confirmedRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        long totalOrders = 0;
        for (OrderStatusTotal total : orderRepository.totalsByStatus(from, to)) {
            countsByStatus.put(total.status(), total.orderCount());
            totalOrders += total.orderCount();
            if (total.status() == OrderStatus.CONFIRMED) {
                confirmedRevenue = total.totalAmount();
            }
        }
        return new OrderSummaryReport(from, to, totalOrders, countsByStatus, confirmedRevenue);
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

    private static void requireValidRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidRequestParameterException("'from' must be before 'to'");
        }
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
