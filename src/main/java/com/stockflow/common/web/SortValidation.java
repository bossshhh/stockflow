package com.stockflow.common.web;

import java.util.Set;

import org.springframework.data.domain.Sort;

import com.stockflow.common.exception.InvalidRequestParameterException;

/** Rejects client sort fields outside a whitelist with a 400 instead of letting them fail as a 500. */
public final class SortValidation {

    private SortValidation() {
    }

    public static void requireAllowed(Sort sort, Set<String> allowedFields) {
        for (Sort.Order order : sort) {
            if (!allowedFields.contains(order.getProperty())) {
                throw new InvalidRequestParameterException(
                        "Cannot sort by '" + order.getProperty() + "'; allowed: " + allowedFields);
            }
        }
    }
}
