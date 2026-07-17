package edu.eci.arsw.testing.controller;

import edu.eci.arsw.testing.dto.CreateOrderRequest;
import edu.eci.arsw.testing.dto.OrderResponse;
import edu.eci.arsw.testing.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints REST para crear y consultar pedidos.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /**
     * Crea un nuevo pedido.
     *
     * @param request payload de creación del pedido
     * @return el pedido creado
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return service.createOrder(request);
    }

    /**
     * Consulta un pedido por su identificador.
     *
     * @param id identificador del pedido
     * @return el pedido encontrado
     */
    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable String id) {
        return service.findById(id);
    }
}
