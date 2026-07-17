package edu.eci.arsw.testing.controller;

import edu.eci.arsw.testing.dto.OrderResponse;
import edu.eci.arsw.testing.service.OrderService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas MockMvc de {@link OrderController}, simulando {@link OrderService}.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService service;

    /**
     * Verifica que una solicitud POST válida crea un pedido y devuelve 201 con sus datos.
     */
    @Test
    void shouldCreateOrder() throws Exception {
        when(service.createOrder(any())).thenReturn(
                new OrderResponse("ORD-1", "CUS-01", 120000, "CREATED", Instant.now())
        );

        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "CUS-01",
                                  "total": 120000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ORD-1"))
                .andExpect(jsonPath("$.customerId").value("CUS-01"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    /**
     * Verifica que un cuerpo de solicitud inválido se rechaza con 400 por la validación de Bean Validation.
     */
    @Test
    void shouldRejectInvalidRequest() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "",
                                  "total": -10
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifica que GET /orders/{id} devuelve 200 con los datos del pedido.
     */
    @Test
    void shouldFindOrderById() throws Exception {
        when(service.findById("ORD-1")).thenReturn(
                new OrderResponse("ORD-1", "CUS-01", 120000, "CREATED", Instant.now())
        );

        mockMvc.perform(get("/orders/ORD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ORD-1"))
                .andExpect(jsonPath("$.customerId").value("CUS-01"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }
}
