package edu.eci.arsw.testing.integration;

import edu.eci.arsw.testing.dto.CreateOrderRequest;
import edu.eci.arsw.testing.dto.OrderResponse;
import edu.eci.arsw.testing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that exercise the full Spring context: controller, service,
 * repository and the in-memory H2 database, with no mocks.
 */
@SpringBootTest
class OrderIntegrationTest {

    @Autowired
    private OrderService service;

    /**
     * Verifies that an order created through the real service can be found by id.
     */
    @Test
    void shouldCreateAndFindOrder() {
        CreateOrderRequest request = new CreateOrderRequest("CUS-99", 250000);

        OrderResponse created = service.createOrder(request);
        OrderResponse found = service.findById(created.id());

        assertEquals(created.id(), found.id());
        assertEquals("CUS-99", found.customerId());
        assertEquals(250000, found.total());
        assertEquals("CREATED", found.status());
    }
}
