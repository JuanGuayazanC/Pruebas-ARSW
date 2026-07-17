package edu.eci.arsw.testing.integration;

import edu.eci.arsw.testing.dto.CreateOrderRequest;
import edu.eci.arsw.testing.dto.OrderResponse;
import edu.eci.arsw.testing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración que ejercitan el contexto completo de Spring: controlador,
 * servicio, repositorio y la base de datos H2 en memoria, sin mocks.
 */
@SpringBootTest
class OrderIntegrationTest {

    @Autowired
    private OrderService service;

    /**
     * Verifica que un pedido creado a través del servicio real puede encontrarse por id.
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
