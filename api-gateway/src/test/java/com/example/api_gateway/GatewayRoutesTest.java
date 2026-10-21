package com.example.api_gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * ROUTING test: the gateway's real behaviour is CONFIGURATION (application.properties), not
 * Java code, so line coverage shows ~0% and says nothing. These tests run the real gateway
 * with its real route config and check where each request ends up.
 *
 * Two stub backends (JDK HttpServer) stand in for order-service and user-service. Each stub
 * echoes the METHOD and PATH it received, so the test proves the path is forwarded unchanged.
 * services.*.url points the routes at the stubs instead of the Kubernetes DNS names.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutesTest {

    private static HttpServer orderStub;
    private static HttpServer userStub;

    @BeforeAll
    static void startStubs() throws IOException {
        orderStub = echoServer("order-stub");
        userStub = echoServer("user-stub");
    }

    @AfterAll
    static void stopStubs() {
        orderStub.stop(0);
        userStub.stop(0);
    }

    /** Answers every request with "<name> got <METHOD> <path>" so the test can see what arrived. */
    private static HttpServer echoServer(String name) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        s.createContext("/", ex -> {
            ex.getRequestBody().readAllBytes();   // consume the request body (POST)
            String got = name + " got " + ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
            byte[] body = got.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        s.start();
        return s;
    }

    @DynamicPropertySource
    static void wireStubs(DynamicPropertyRegistry r) {
        r.add("services.order.url", () -> "http://localhost:" + orderStub.getAddress().getPort());
        r.add("services.user.url", () -> "http://localhost:" + userStub.getAddress().getPort());
    }

    @Autowired
    private WebTestClient client;

    @Test
    void userPathIsForwardedUnchangedToUserService() {
        client.get().uri("/api/v1/users/1").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("user-stub got GET /api/v1/users/1");
    }

    @Test
    void createOrderKeepsMethodAndPath() {
        client.post().uri("/api/v1/orders").bodyValue("{\"userId\":1,\"item\":\"book\"}").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("order-stub got POST /api/v1/orders");
    }

    @Test
    void orderByIdGoesToOrderService() {
        client.get().uri("/api/v1/orders/42").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("order-stub got GET /api/v1/orders/42");
    }

    @Test
    void pathWithoutARouteIs404AndReachesNoBackend() {
        client.get().uri("/api/v1/payments/1").exchange().expectStatus().isNotFound();
    }

    @Test
    void prefixMatchingIsBySegmentNotByText() {
        // Path=/api/v1/orders/** must not swallow /api/v1/ordersX: a different word is a different route.
        client.get().uri("/api/v1/ordersX/1").exchange().expectStatus().isNotFound();
    }

    @Test
    void oldDoubledPathsAreGone() {
        // Before ADR-0007: /order/order and /user/user (prefix stripped, then added again by the service).
        client.get().uri("/order/order").exchange().expectStatus().isNotFound();
        client.get().uri("/user/user").exchange().expectStatus().isNotFound();
    }
}
