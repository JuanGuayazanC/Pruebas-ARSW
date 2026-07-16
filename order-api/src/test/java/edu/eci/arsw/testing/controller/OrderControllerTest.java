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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService service;

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
}
