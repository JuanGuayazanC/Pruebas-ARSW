package edu.eci.arsw.testing.service;

import edu.eci.arsw.testing.dto.CreateOrderRequest;
import edu.eci.arsw.testing.dto.OrderResponse;
import edu.eci.arsw.testing.model.Order;
import edu.eci.arsw.testing.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Lógica de negocio para crear y consultar pedidos.
 */
@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /**
     * Crea un nuevo pedido a partir de la solicitud recibida.
     *
     * @param request datos requeridos para crear el pedido
     * @return el pedido creado
     * @throws IllegalArgumentException si el total del pedido supera el valor máximo permitido
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
     * Busca un pedido por su identificador.
     *
     * @param id identificador del pedido
     * @return el pedido encontrado
     * @throws IllegalArgumentException si no existe ningún pedido con ese id
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
