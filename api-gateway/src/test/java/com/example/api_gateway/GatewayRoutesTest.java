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
 * echoes the path it RECEIVED, so the test sees the effect of the StripPrefix=1 filter.
 * Eureka is off; the load balancer resolves lb://ORDER-SERVICE and lb://USER-SERVICE through a
 * static list (simple discovery) pointing at the stubs.
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

    /** Answers every path with "<name> got <path>" so the test can see what arrived. */
    private static HttpServer echoServer(String name) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        s.createContext("/", ex -> {
            byte[] body = (name + " got " + ex.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8);
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
        r.add("eureka.client.enabled", () -> "false");
        r.add("spring.cloud.discovery.client.simple.instances[ORDER-SERVICE][0].uri",
                () -> "http://localhost:" + orderStub.getAddress().getPort());
        r.add("spring.cloud.discovery.client.simple.instances[USER-SERVICE][0].uri",
                () -> "http://localhost:" + userStub.getAddress().getPort());
    }

    @Autowired
    private WebTestClient client;

    @Test
    void orderPathGoesToOrderServiceWithPrefixStripped() {
        // Ingress path /order/order -> gateway strips the first segment -> order-service gets /order
        client.get().uri("/order/order").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("order-stub got /order");
    }

    @Test
    void userPathGoesToUserServiceWithPrefixStripped() {
        client.get().uri("/user/user").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("user-stub got /user");
    }

    @Test
    void deeperPathsKeepEverythingAfterThePrefix() {
        client.get().uri("/order/order/42/items").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("order-stub got /order/42/items");
    }

    @Test
    void pathWithoutARouteIs404AndReachesNoBackend() {
        client.get().uri("/payments/pay").exchange().expectStatus().isNotFound();
    }

    @Test
    void prefixMatchingIsBySegmentNotByText() {
        // Path=/order/** must not swallow /orders/...: a different word is a different route.
        client.get().uri("/orders/1").exchange().expectStatus().isNotFound();
    }
}
