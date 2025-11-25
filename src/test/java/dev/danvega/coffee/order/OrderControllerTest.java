package dev.danvega.coffee.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * Controller tests using RestTestClient - Spring Framework 7's unified REST testing API.
 *
 * RestTestClient provides a consistent API across different testing scenarios:
 * - bindToController() - Unit test without Spring context (fastest)
 * - bindTo(mockMvc) - MVC slice test with validation/security (used here)
 * - bindToApplicationContext() - Full integration test
 * - bindToServer() - Real HTTP server test
 *
 * See CoffeeControllerTest for an alternative approach using MockMvcTester with AssertJ.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderRepository orderRepository;

    @MockitoBean
    OrderItemRepository orderItemRepository;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @Test
    void shouldReturnAllOrders() {
        var orders = List.of(
            new Order(1L, 1L, "Alice", LocalDateTime.now(), new BigDecimal("10.00"), OrderStatus.PENDING)
        );
        when(orderRepository.findAll()).thenReturn(orders);

        client.get().uri("/api/orders")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void shouldReturnOrdersByCustomer() {
        var orders = List.of(
            new Order(1L, 1L, "Alice", LocalDateTime.now(), new BigDecimal("10.00"), OrderStatus.DELIVERED)
        );
        when(orderRepository.findByCustomerName("Alice")).thenReturn(orders);

        client.get().uri("/api/orders/customer/Alice")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].customerName").isEqualTo("Alice");
    }

    @Test
    void shouldReturnOrdersByCoffee() {
        var orders = List.of(
            new Order(1L, 1L, "Bob", LocalDateTime.now(), new BigDecimal("5.00"), OrderStatus.READY)
        );
        when(orderRepository.findOrdersByCoffeeName("Latte")).thenReturn(orders);

        client.get().uri("/api/orders/by-coffee?coffeeName=Latte")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void shouldReturnOrderItems() {
        var items = List.of(
            new OrderItem(1L, 1L, 1L, 2, new BigDecimal("4.50"))
        );
        when(orderItemRepository.findOrderItemsWithCoffeeDetails(1L)).thenReturn(items);

        client.get().uri("/api/orders/1/items")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].quantity").isEqualTo(2);
    }
}
