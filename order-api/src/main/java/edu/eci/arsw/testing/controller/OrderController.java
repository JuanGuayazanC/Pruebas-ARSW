package edu.eci.arsw.testing.controller;

import edu.eci.arsw.testing.dto.CreateOrderRequest;
import edu.eci.arsw.testing.dto.OrderResponse;
import edu.eci.arsw.testing.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for creating and querying orders.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /**
     * Creates a new order.
     *
     * @param request order creation payload
     * @return the created order
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return service.createOrder(request);
    }

    /**
     * Retrieves an order by its identifier.
     *
     * @param id order identifier
     * @return the matching order
     */
    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable String id) {
        return service.findById(id);
    }
}
