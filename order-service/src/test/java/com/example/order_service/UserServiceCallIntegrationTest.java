package com.example.order_service;

import com.example.order_service.client.UserClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INTEGRATION test: the real order-service (full Spring context, real RestTemplate with its
 * timeouts, real Spring Cloud LoadBalancer) talks over real HTTP to a STUB user-service.
 *
 * The stub is the JDK's built-in HttpServer (no extra library): we decide per test whether
 * "user-service" answers normally, fails with 500, or is too slow. Eureka is switched off;
 * the load balancer finds USER-SERVICE through a static list (simple discovery) pointing at
 * the stub. This is how you test failure cases that are hard to produce with real services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserServiceCallIntegrationTest {

    enum Mode { OK, ERROR_500, SLOW_3S }

    private static HttpServer stub;
    private static volatile Mode mode = Mode.OK;

    @BeforeAll
    static void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stub.createContext("/user", exchange -> {
            try {
                switch (mode) {
                    case OK -> reply(exchange, 200, "stub-user");
                    case ERROR_500 -> reply(exchange, 500, "boom");
                    case SLOW_3S -> {           // longer than order-service's 2 s read timeout
                        Thread.sleep(3000);
                        reply(exchange, 200, "too late");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // the client already gave up (timeout) and closed the connection
            }
        });
        stub.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        stub.start();
    }

    @AfterAll
    static void stopStub() {
        stub.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @DynamicPropertySource
    static void wireStub(DynamicPropertyRegistry registry) {
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.client.simple.instances[USER-SERVICE][0].uri",
                () -> "http://localhost:" + stub.getAddress().getPort());
    }

    @Autowired
    private UserClient userClient;

    @LocalServerPort
    private int orderServicePort;

    @BeforeEach
    void reset() {
        mode = Mode.OK;
    }

    // ---- the client on its own --------------------------------------------------------------

    @Test
    void returnsWhatUserServiceAnswers() {
        assertThat(userClient.getUser()).isEqualTo("stub-user");
    }

    @Test
    void userService500BecomesHttpServerErrorException() {
        mode = Mode.ERROR_500;

        assertThatThrownBy(userClient::getUser).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void slowUserServiceIsCutOffByTheReadTimeout() {
        mode = Mode.SLOW_3S;
        long start = System.nanoTime();

        assertThatThrownBy(userClient::getUser).isInstanceOf(ResourceAccessException.class);

        long tookMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        // RestConfig sets readTimeout=2000 ms. Without it this call would hang for the full 3 s
        // (or forever against a stuck server) and hold an order-service thread the whole time.
        assertThat(tookMs).isBetween(1_900L, 2_900L);
    }

    @Test
    void userServiceNotListeningFailsFastOnConnect() throws IOException {
        int deadPort;
        try (ServerSocket s = new ServerSocket(0)) {   // grab a free port, then close it: nothing listens there
            deadPort = s.getLocalPort();
        }
        org.springframework.web.client.RestTemplate plain = new org.springframework.web.client.RestTemplate();
        long start = System.nanoTime();

        assertThatThrownBy(() -> plain.getForObject("http://localhost:" + deadPort + "/user", String.class))
                .isInstanceOf(ResourceAccessException.class);

        // "Connection refused" comes back immediately; the 1 s connect timeout matters when the
        // host does not answer at all (e.g. a dead pod IP), which a unit test can't reproduce.
        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isLessThan(1_000L);
    }

    // ---- the whole order-service over HTTP ---------------------------------------------------

    @Test
    void getOrderEndToEndWhenUserServiceIsHealthy() throws Exception {
        HttpResponse<String> res = callOrder();

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo("Order created for -> stub-user");
    }

    @Test
    void getOrderTodayReturns500WhenUserServiceFails() throws Exception {
        // CHARACTERIZATION test: it pins down CURRENT behaviour, not desired behaviour.
        // Today a user-service failure becomes a generic 500 for the caller. The resilience
        // phase (Resilience4j: retry, circuit breaker, fallback) will change this on purpose,
        // and this test will be updated to the new contract (e.g. 503 + clear message).
        mode = Mode.ERROR_500;

        assertThat(callOrder().statusCode()).isEqualTo(500);
    }

    private HttpResponse<String> callOrder() throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + orderServicePort + "/order")).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
