package com.stockflow.product;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockflow.common.web.SortValidation;
import com.stockflow.product.dto.CreateProductRequest;
import com.stockflow.product.dto.ProductResponse;
import com.stockflow.product.dto.UpdateProductRequest;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /** Clients may only sort on these entity fields; anything else is a 400, not a 500. */
    static final Set<String> SORTABLE_FIELDS = Set.of("id", "sku", "name", "price", "createdAt", "updatedAt");

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ProductService(ProductRepository productRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String sku = normalizeSku(request.sku());
        // Fast path: a clean 409 without hitting the constraint in the common case.
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }
        Product product = new Product(sku, request.name().strip(), normalizeDescription(request.description()),
                request.price(), clock.instant());
        Product saved;
        try {
            // Flush now so a constraint violation surfaces here, where we can translate it.
            saved = productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException ex) {
            // Race: another transaction inserted the same SKU between our check and our insert.
            if (isSkuUniqueViolation(ex)) {
                throw new DuplicateSkuException(sku);
            }
            throw ex;
        }
        // Listeners run synchronously in this transaction: if they fail, the product is rolled back too.
        eventPublisher.publishEvent(new ProductCreatedEvent(saved.getId()));
        log.info("Created product id={} sku={}", saved.getId(), saved.getSku());
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(long id) {
        return ProductResponse.from(findOrThrow(id));
    }

    /**
     * Loads several products in one query.
     *
     * @throws ProductNotFoundException for the first id that does not exist
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> getByIds(Set<Long> ids) {
        List<Product> found = productRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            Set<Long> foundIds = found.stream().map(Product::getId).collect(Collectors.toSet());
            long missing = ids.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
            throw new ProductNotFoundException(missing);
        }
        return found.stream().map(ProductResponse::from).toList();
    }

    /**
     * @param active {@code null} returns products regardless of status
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Boolean active, Pageable pageable) {
        SortValidation.requireAllowed(pageable.getSort(), SORTABLE_FIELDS);
        Page<Product> page = active == null
                ? productRepository.findAll(pageable)
                : productRepository.findAllByActive(active, pageable);
        return page.map(ProductResponse::from);
    }

    @Transactional
    public ProductResponse update(long id, UpdateProductRequest request) {
        Product product = findOrThrow(id);
        product.updateDetails(request.name().strip(), normalizeDescription(request.description()),
                request.price(), clock.instant());
        return ProductResponse.from(product);
    }

    @Transactional
    public void deactivate(long id) {
        Product product = findOrThrow(id);
        product.deactivate(clock.instant());
        log.info("Deactivated product id={} sku={}", product.getId(), product.getSku());
    }

    private Product findOrThrow(long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    static String normalizeSku(String sku) {
        return sku.strip().toUpperCase(Locale.ROOT);
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.strip();
    }

    private static boolean isSkuUniqueViolation(DataIntegrityViolationException ex) {
        return ex.getCause() instanceof ConstraintViolationException violation
                && Product.SKU_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
    }
}
