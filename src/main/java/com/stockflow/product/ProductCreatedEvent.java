package com.stockflow.product;

/**
 * Published inside the transaction that creates a product. Lets other features react (inventory creates the
 * product's stock row) without the product feature depending on them.
 */
public record ProductCreatedEvent(long productId) {
}
