# ADR-0006: Kubernetes DNS for service discovery; Eureka removed

- **Status:** Accepted
- **Date:** 2026-10-22
- **Related:** ADR-0005 (NetworkPolicies, whose Eureka rules go away), ADR-0007 (API paths)

## Context
Pods are replaced all the time, and each new pod gets a new IP. Callers need a way to find a
working instance of the service they call: that is service discovery.

The lab ran two discovery systems for the same job:
1. **Eureka (client-side discovery).** Each service registered its pod IP with the Eureka server
   and renewed it every 30 s. Callers (`lb://USER-SERVICE` in the gateway, a `@LoadBalanced`
   RestTemplate in order-service) downloaded the registry and picked an instance themselves.
2. **Kubernetes Service + DNS (server-side discovery).** Every service already had a Service.
   CoreDNS resolves `user-service` to a stable ClusterIP. The EndpointSlice lists only pods whose
   readiness probe passes. kube-proxy forwards each new connection to one of them.

Running both had real costs:
- **Stale instances.** A dead pod stays in Eureka until its lease expires (up to ~90 s), and
  callers keep sending requests to it. Kubernetes drops a pod from the endpoints within seconds
  of a failed readiness probe.
- **Two health systems.** Eureka heartbeats and Kubernetes readiness can disagree.
- **An extra component to run.** The Eureka server is one more image to build and scan, one
  more pod, extra NetworkPolicy rules, and a startup-order dependency.

## Options considered
| Option | Pros | Cons |
|---|---|---|
| A. Keep Eureka | No change | Duplicate system; stale-instance errors after pod deaths |
| B. Spring Cloud Kubernetes discovery | Keeps `lb://` and per-request client-side balancing | Pods need RBAC to read the API server; one more library |
| C. Plain Kubernetes DNS | No discovery code at all; same URLs in Kubernetes and docker-compose | Balancing is per connection (kube-proxy, L4), not per request |

## Decision
Option C.
- Callers use plain URLs from configuration:
  - `services.order.url` / `services.user.url` in the gateway;
  - `clients.user-service.base-url` in order-service (a `RestClient` with 1 s connect and 2 s read timeouts).
- The `discovery-server` module and the `eureka-client` dependency are removed.

## Consequences
- **Good:** fewer moving parts, and dead pods leave the rotation in seconds. Code no longer knows
  how discovery works, and docker-compose uses the same names.
- **Bad:** kube-proxy balances per TCP connection. A caller that reuses one keep-alive connection
  sends all its requests to the same pod, so the spread is uneven. `X-Served-By` on user-service
  responses makes this visible. A service mesh (per-request L7 balancing) would fix it if it ever
  matters.
- **Deployment:** this change ships in two steps:
  1. this PR changes code and images;
  2. the tag-bump PR deploys them and, in the same commit, deletes `k8s/apps/eureka.yaml`, the
     Eureka NetworkPolicy rules and the old probes.

  Deleting Eureka before the new images run would break the running old images, which still
  look up `USER-SERVICE` in Eureka.
