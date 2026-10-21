package com.example.user_service.api;

import com.example.user_service.domain.User;
import com.example.user_service.domain.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for users (ADR-0007): plural noun, version in the path, JSON in and out.
 * The path is the SAME outside (through ingress-nginx and the gateway) and inside the cluster;
 * nothing rewrites it on the way.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final String servedBy;

    public UserController(UserService userService, @Value("${HOSTNAME:local}") String servedBy) {
        this.userService = userService;
        this.servedBy = servedBy;   // in Kubernetes HOSTNAME is the pod name
    }

    /**
     * 200 + the user, or 404 (RFC 9457 problem JSON, see ApiErrors).
     * X-Served-By names the pod that answered: send several requests and watch the load
     * balancing (per connection through kube-proxy, per request through a proxy like nginx).
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable long id) {
        User user = userService.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        return ResponseEntity.ok().header("X-Served-By", servedBy).body(user);
    }
}
