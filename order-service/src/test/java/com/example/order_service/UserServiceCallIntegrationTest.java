package com.example.order_service;

import com.example.order_service.client.UserClient;
import com.example.order_service.domain.UserServiceUnavailableException;
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
import org.springframework.web.client.RestClient;

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
 * INTEGRATION test: the real order-service (full Spring context, real RestClient with its
 * timeouts, real JSON and error mapping) talks over real HTTP to a STUB user-service.
 *
 * The stub is the JDK's built-in HttpServer (no extra library): each test decides whether
 * "user-service" knows the user, says 404, fails with 500, or is too slow. The client finds it
 * through clients.user-service.base-url, exactly as it finds http://user-service:8080 in
 * Kubernetes (ADR-0006): no registry involved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserServiceCallIntegrationTest {

    enum Mode { FOUND, NOT_FOUND, ERROR_500, SLOW_3S }

    private static HttpServer stub;
    private static volatile Mode mode = Mode.FOUND;
    private static volatile String lastPath;

    @BeforeAll
    static void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stub.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            try {
                switch (mode) {
                    case FOUND -> reply(exchange, 200, "{\"id\":1,\"name\":\"stub\",\"age\":1}");
                    case NOT_FOUND -> reply(exchange, 404, "{\"status\":404}");
                    case ERROR_500 -> reply(exchange, 500, "boom");
                    case SLOW_3S -> {           // longer than order-service's 2 s read timeout
                        Thread.sleep(3000);
                        reply(exchange, 200, "{}");
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
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @DynamicPropertySource
    static void wireStub(DynamicPropertyRegistry registry) {
        registry.add("clients.user-service.base-url", () -> "http://localhost:" + stub.getAddress().getPort());
    }

    @Autowired
    private UserClient userClient;

    @LocalServerPort
    private int orderServicePort;

    @BeforeEach
    void reset() {
        mode = Mode.FOUND;
        lastPath = null;
    }

    // ---- the client on its own --------------------------------------------------------------

    @Test
    void callsTheVersionedUserPath() {
        assertThat(userClient.userExists(1)).isTrue();
        assertThat(lastPath).isEqualTo("/api/v1/users/1");
    }

    @Test
    void user404MeansTheUserDoesNotExist() {
        mode = Mode.NOT_FOUND;

        assertThat(userClient.userExists(1)).isFalse();
    }

    @Test
    void user500IsUnavailableNotNotFound() {
        // A broken user-service must never be reported as "no such user".
        mode = Mode.ERROR_500;

        assertThatThrownBy(() -> userClient.userExists(1)).isInstanceOf(UserServiceUnavailableException.class);
    }

    @Test
    void slowUserServiceIsCutOffByTheReadTimeout() {
        mode = Mode.SLOW_3S;
        long start = System.nanoTime();

        assertThatThrownBy(() -> userClient.userExists(1)).isInstanceOf(UserServiceUnavailableException.class);

        long tookMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        // UserClientConfig sets readTimeout=2000 ms. Without it this call would hang for the full
        // 3 s (or forever against a stuck server) and hold an order-service thread the whole time.
        assertThat(tookMs).isBetween(1_900L, 2_900L);
    }

    @Test
    void userServiceNotListeningFailsFastOnConnect() throws IOException {
        int deadPort;
        try (ServerSocket s = new ServerSocket(0)) {   // grab a free port, then close it: nothing listens there
            deadPort = s.getLocalPort();
        }
        UserClient toNowhere = new UserClient(RestClient.create("http://localhost:" + deadPort));
        long start = System.nanoTime();

        assertThatThrownBy(() -> toNowhere.userExists(1)).isInstanceOf(UserServiceUnavailableException.class);

        // "Connection refused" comes back immediately; the 1 s connect timeout matters when the
        // host does not answer at all (e.g. a dead pod IP), which a unit test can't reproduce.
        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isLessThan(1_000L);
    }

    // ---- the whole order-service over HTTP ---------------------------------------------------

    @Test
    void createThenReadAnOrderEndToEnd() throws Exception {
        HttpResponse<String> created = post("{\"userId\":1,\"item\":\"book\"}");

        assertThat(created.statusCode()).isEqualTo(201);
        String location = created.headers().firstValue("Location").orElseThrow();
        assertThat(location).matches("/api/v1/orders/\\d+");
        assertThat(created.body()).contains("\"item\":\"book\"");

        HttpResponse<String> read = send(HttpRequest.newBuilder(uri(location)).build());
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(read.body()).isEqualTo(created.body());
    }

    @Test
    void unknownUserIs422() throws Exception {
        mode = Mode.NOT_FOUND;

        assertThat(post("{\"userId\":1,\"item\":\"book\"}").statusCode()).isEqualTo(422);
    }

    @Test
    void userServiceFailureIs503NotA500() throws Exception {
        // Before this change every user-service failure surfaced as a generic 500. Now the
        // contract says: the dependency is down, retry later (503 + Retry-After).
        mode = Mode.ERROR_500;

        HttpResponse<String> res = post("{\"userId\":1,\"item\":\"book\"}");
        assertThat(res.statusCode()).isEqualTo(503);
        assertThat(res.headers().firstValue("Retry-After")).contains("5");
    }

    private HttpResponse<String> post(String json) throws Exception {
        return send(HttpRequest.newBuilder(uri("/api/v1/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + orderServicePort + path);
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
