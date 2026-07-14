package edu.eci.arsw.testing.service;

import edu.eci.arsw.testing.dto.CreateOrderRequest;
import edu.eci.arsw.testing.dto.OrderResponse;
import edu.eci.arsw.testing.model.Order;
import edu.eci.arsw.testing.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Business logic for creating and retrieving orders.
 */
@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new order from the given request.
     *
     * @param request data required to create the order
     * @return the created order
     * @throws IllegalArgumentException if the order total exceeds the maximum allowed value
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (request.total() > 5_000_000) {
            throw new IllegalArgumentException("El pedido supera el valor máximo permitido");
        }

        Order order = new Order(
                "ORD-" + UUID.randomUUID(),
                request.customerId(),
                request.total(),
                "CREATED",
                Instant.now()
        );

        Order saved = repository.save(order);

        return new OrderResponse(
                saved.getId(), saved.getCustomerId(), saved.getTotal(),
                saved.getStatus(), saved.getCreatedAt()
        );
    }

    /**
     * Finds an order by its identifier.
     *
     * @param id order identifier
     * @return the matching order
     * @throws IllegalArgumentException if no order exists with the given id
     */
    public OrderResponse findById(String id) {
        Order order = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        return new OrderResponse(
                order.getId(), order.getCustomerId(), order.getTotal(),
                order.getStatus(), order.getCreatedAt()
        );
    }
}
