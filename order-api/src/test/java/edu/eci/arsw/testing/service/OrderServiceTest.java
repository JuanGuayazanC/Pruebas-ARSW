package edu.eci.arsw.testing.service;

import edu.eci.arsw.testing.dto.CreateOrderRequest;
import edu.eci.arsw.testing.dto.OrderResponse;
import edu.eci.arsw.testing.model.Order;
import edu.eci.arsw.testing.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de {@link OrderService}, simulando {@link OrderRepository}.
 */
class OrderServiceTest {

    /**
     * Verifica que una solicitud válida se persiste y se mapea correctamente al DTO de respuesta.
     */
    @Test
    void shouldCreateOrderWhenRequestIsValid() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderService service = new OrderService(repository);

        Order savedOrder = new Order("ORD-1", "CUS-01", 120000, "CREATED", Instant.now());
        when(repository.save(any(Order.class))).thenReturn(savedOrder);

        CreateOrderRequest request = new CreateOrderRequest("CUS-01", 120000);
        OrderResponse response = service.createOrder(request);

        assertNotNull(response);
        assertEquals("ORD-1", response.id());
        assertEquals("CUS-01", response.customerId());
        assertEquals(120000, response.total());
        assertEquals("CREATED", response.status());
        verify(repository, times(1)).save(any(Order.class));
    }

    /**
     * Verifica que un total superior al límite permitido se rechaza antes de tocar el repositorio.
     */
    @Test
    void shouldRejectOrderWhenTotalExceedsLimit() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderService service = new OrderService(repository);

        CreateOrderRequest request = new CreateOrderRequest("CUS-01", 6000000);

        assertThrows(IllegalArgumentException.class, () -> service.createOrder(request));
        verify(repository, never()).save(any(Order.class));
    }

    /**
     * Verifica que un pedido existente se mapea al DTO de respuesta cuando se encuentra.
     */
    @Test
    void shouldReturnOrderWhenIdExists() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderService service = new OrderService(repository);

        Order existingOrder = new Order("ORD-1", "CUS-01", 120000, "CREATED", Instant.now());
        when(repository.findById("ORD-1")).thenReturn(Optional.of(existingOrder));

        OrderResponse response = service.findById("ORD-1");

        assertNotNull(response);
        assertEquals("ORD-1", response.id());
        assertEquals("CUS-01", response.customerId());
        assertEquals(120000, response.total());
        assertEquals("CREATED", response.status());
    }

    /**
     * Verifica que buscar un pedido inexistente lanza {@link IllegalArgumentException}.
     */
    @Test
    void shouldThrowExceptionWhenOrderNotFound() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderService service = new OrderService(repository);

        when(repository.findById("ORD-404")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.findById("ORD-404"));
    }
}
