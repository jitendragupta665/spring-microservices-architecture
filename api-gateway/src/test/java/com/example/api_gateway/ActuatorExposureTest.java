package com.example.api_gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Phase 0 exit check: the gateway is the only service reachable from outside (via the Ingress),
 * so it must expose nothing but health and info. Everything else leaks data (heapdump, env,
 * configprops) or lets an anonymous caller change the running system (loggers, refresh,
 * serviceregistry).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false") // no discovery server in a unit-test JVM
@AutoConfigureWebTestClient
class ActuatorExposureTest {

    @Autowired
    WebTestClient client;

    @ParameterizedTest(name = "{0} is not exposed")
    @ValueSource(strings = {
            "/actuator/heapdump", "/actuator/threaddump", "/actuator/env", "/actuator/configprops",
            "/actuator/beans", "/actuator/mappings", "/actuator/loggers", "/actuator/refresh",
            "/actuator/serviceregistry", "/actuator/metrics", "/actuator/conditions"})
    void sensitiveEndpointsReturn404(String path) {
        client.get().uri(path).exchange().expectStatus().isNotFound();
    }

    @Test
    void healthStaysAvailableForProbes() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
