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
import com.stockflow.user.CurrentUser;
import com.stockflow.user.UserService;
import com.stockflow.user.dto.UserResponse;

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
    private final UserService userService;
    private final OrderProperties orderProperties;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, ProductService productService,
                        InventoryService inventoryService, UserService userService,
                        OrderProperties orderProperties, Clock clock) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.userService = userService;
        this.orderProperties = orderProperties;
        this.clock = clock;
    }

    /** Places an order for the calling customer; name and email are copied from their account. */
    @Transactional
    public OrderResponse create(CurrentUser customer, CreateOrderRequest request) {
        // TreeMap: duplicates merged AND iterated in ascending product id (the lock order).
        SortedMap<Long, Integer> quantities = mergeQuantities(request.items());
        requireMergedQuantitiesWithinLimit(quantities);
        Map<Long, ProductResponse> products = productService.getByIds(quantities.keySet()).stream()
                .collect(Collectors.toMap(ProductResponse::id, Function.identity()));

        UserResponse account = userService.getById(customer.id());
        CustomerOrder order = new CustomerOrder(account.id(), account.fullName(), account.email(), clock.instant());
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
    public OrderResponse getById(CurrentUser actor, long id) {
        CustomerOrder order = orderRepository.findWithItemsById(id).orElseThrow(() -> new OrderNotFoundException(id));
        requireVisibleTo(order, actor);
        return OrderResponse.from(order);
    }

    /**
     * Every filter is optional. Customers always get only their own orders, whatever they ask for.
     * Results are summaries without items (no N+1); default order is newest first.
     */
    @Transactional(readOnly = true)
    public Page<OrderListItemResponse> search(CurrentUser actor, OrderSearchCriteria requested, Pageable pageable) {
        OrderSearchCriteria criteria = actor.isCustomer() ? requested.withCustomerId(actor.id()) : requested;
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
            // A packed, shipped or delivered order is still a confirmed sale: without this, revenue would
            // drop every time the warehouse moved an order forward.
            if (total.status().isFulfilmentStage()) {
                confirmedRevenue = confirmedRevenue.add(total.totalAmount());
            }
        }
        return new OrderSummaryReport(from, to, totalOrders, countsByStatus, confirmedRevenue);
    }

    @Transactional
    public OrderResponse confirm(CurrentUser actor, long id) {
        CustomerOrder order = lockOrThrow(id);
        order.confirm(clock.instant(), actor.id());
        itemsInLockOrder(order).forEach(item ->
                inventoryService.fulfill(item.getProductId(), item.getQuantity(), order.getId()));
        log.info("Confirmed order id={}", order.getId());
        return OrderResponse.from(order);
    }

    /** Staff may cancel any order; a customer only their own. */
    @Transactional
    public OrderResponse cancel(CurrentUser actor, long id) {
        CustomerOrder order = lockOrThrow(id);
        requireVisibleTo(order, actor);
        OrderStatus previous = order.getStatus();
        Instant now = clock.instant();
        order.cancel(now, orderProperties.confirmedCancelWindow(), actor.id());

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

    /** Warehouse: the confirmed order's items are packed. From here on it can no longer be cancelled. */
    @Transactional
    public OrderResponse pack(CurrentUser actor, long id) {
        CustomerOrder order = lockOrThrow(id);
        order.pack(clock.instant(), actor.id());
        log.info("Packed order id={} by userId={}", order.getId(), actor.id());
        return OrderResponse.from(order);
    }

    /** Warehouse: the packed order left the building. No stock change: stock left at confirmation. */
    @Transactional
    public OrderResponse ship(CurrentUser actor, long id) {
        CustomerOrder order = lockOrThrow(id);
        order.ship(clock.instant(), actor.id());
        log.info("Shipped order id={} by userId={}", order.getId(), actor.id());
        return OrderResponse.from(order);
    }

    /** Warehouse: the shipped order reached the customer. Terminal. */
    @Transactional
    public OrderResponse deliver(CurrentUser actor, long id) {
        CustomerOrder order = lockOrThrow(id);
        order.deliver(clock.instant(), actor.id());
        log.info("Delivered order id={} by userId={}", order.getId(), actor.id());
        return OrderResponse.from(order);
    }

    private static void requireValidRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidRequestParameterException("'from' must be before 'to'");
        }
    }

    /**
     * A customer asking for someone else's order gets 404, not 403: a 403 would confirm that the order
     * exists, letting anyone discover other customers' order ids.
     */
    private static void requireVisibleTo(CustomerOrder order, CurrentUser actor) {
        if (actor.isCustomer() && !order.isOwnedBy(actor.id())) {
            throw new OrderNotFoundException(order.getId());
        }
    }

    private CustomerOrder lockOrThrow(long id) {
        return orderRepository.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private static List<OrderItem> itemsInLockOrder(CustomerOrder order) {
        return order.getItems().stream().sorted(LOCK_ORDER).toList();
    }

    /**
     * Bean Validation checks each request line; duplicates are merged afterwards, so 6,000 + 6,000 of one
     * product would otherwise slip past the 10,000 limit.
     */
    private static void requireMergedQuantitiesWithinLimit(SortedMap<Long, Integer> quantities) {
        quantities.forEach((productId, quantity) -> {
            if (quantity > OrderItemRequest.MAX_QUANTITY) {
                throw new InvalidRequestParameterException("Total quantity for product " + productId
                        + " is " + quantity + "; at most " + OrderItemRequest.MAX_QUANTITY + " per order");
            }
        });
    }

    private static SortedMap<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        SortedMap<Long, Integer> merged = new TreeMap<>();
        for (OrderItemRequest item : items) {
            merged.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return merged;
    }
}
