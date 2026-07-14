package edu.eci.arsw.testing.dto;

import java.time.Instant;

/**
 * Response payload representing an order returned by the API.
 *
 * @param id order identifier
 * @param customerId identifier of the customer that placed the order
 * @param total order total amount
 * @param status current order status
 * @param createdAt creation timestamp
 */
public record OrderResponse(
        String id,
        String customerId,
        double total,
        String status,
        Instant createdAt
) {}
