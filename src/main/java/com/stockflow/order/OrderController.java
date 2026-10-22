package com.stockflow.order;

import java.net.URI;
import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.stockflow.common.web.PageResponse;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderListItemResponse;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.dto.OrderSummaryReport;
import com.stockflow.user.CurrentUser;

@RestController
@Tag(name = "Orders", description = "Order lifecycle: PENDING -> CONFIRMED -> CANCELLED")
@RequestMapping(OrderController.BASE_PATH)
public class OrderController {

    static final String BASE_PATH = "/api/v1/orders";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Place an order (CUSTOMER): reserves stock for every item, all or nothing")
    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(CurrentUser.from(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Search orders (customers only ever see their own). All filters optional and combinable; createdFrom inclusive, createdTo "
            + "exclusive (ISO-8601, e.g. 2026-01-15T00:00:00Z). Newest first unless sorted by id, createdAt, "
            + "totalAmount or status")
    @GetMapping
    public ResponseEntity<PageResponse<OrderListItemResponse>> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        OrderSearchCriteria criteria = new OrderSearchCriteria(null, status, customerEmail, createdFrom, createdTo);
        return ResponseEntity.ok(PageResponse.from(orderService.search(CurrentUser.from(jwt), criteria, pageable)));
    }

    @Operation(summary = "Report (STAFF): number of orders per status and confirmed revenue for orders created in [from, to)")
    @GetMapping("/summary")
    public ResponseEntity<OrderSummaryReport> summary(@RequestParam Instant from, @RequestParam Instant to) {
        return ResponseEntity.ok(orderService.summarize(from, to));
    }

    @Operation(summary = "Get one order with its items (customers: own orders only)")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return ResponseEntity.ok(orderService.getById(CurrentUser.from(jwt), id));
    }

    /** Status changes are actions (POST), not field edits (PUT/PATCH): each has rules and side effects. */
    @Operation(summary = "Confirm a PENDING order (STAFF): reserved stock leaves the warehouse")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirm(@PathVariable long id) {
        return ResponseEntity.ok(orderService.confirm(id));
    }

    @Operation(summary = "Cancel an order (customers: own orders only): PENDING releases its reservation; "
            + "CONFIRMED (within 5 minutes) is restocked")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return ResponseEntity.ok(orderService.cancel(CurrentUser.from(jwt), id));
    }
}
