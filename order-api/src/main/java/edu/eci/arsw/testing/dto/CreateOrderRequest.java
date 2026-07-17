package edu.eci.arsw.testing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de solicitud para crear un nuevo pedido.
 *
 * @param customerId identificador del cliente que realiza el pedido, no debe estar vacío
 * @param total valor total del pedido, debe ser al menos 1
 */
public record CreateOrderRequest(
        @NotBlank String customerId,
        @Min(1) double total
) {}
