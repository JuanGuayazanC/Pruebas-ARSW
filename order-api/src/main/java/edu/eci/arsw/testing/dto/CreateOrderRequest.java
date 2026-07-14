package edu.eci.arsw.testing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for creating a new order.
 *
 * @param customerId identifier of the customer placing the order, must not be blank
 * @param total order total amount, must be at least 1
 */
public record CreateOrderRequest(
        @NotBlank String customerId,
        @Min(1) double total
) {}
