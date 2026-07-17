package edu.eci.arsw.testing.dto;

import java.time.Instant;

/**
 * Payload de respuesta que representa un pedido devuelto por la API.
 *
 * @param id identificador del pedido
 * @param customerId identificador del cliente que realizó el pedido
 * @param total valor total del pedido
 * @param status estado actual del pedido
 * @param createdAt fecha y hora de creación
 */
public record OrderResponse(
        String id,
        String customerId,
        double total,
        String status,
        Instant createdAt
) {}
