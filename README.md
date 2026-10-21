# Spring microservices lab: v1 (Docker Compose era)

> **Archived snapshot, for learning only.** This branch is the state of the project *before*
> Kubernetes (February 2026): four Spring Boot services, Netflix Eureka for service discovery,
> Spring Cloud Gateway, one Dockerfile per service, and Docker Compose to run them together.
> It is frozen on purpose. Its libraries have known vulnerabilities and it has no tests, CI or
> security hardening. Do not deploy it anywhere. The current project lives on
> [`main`](https://github.com/jitendragupta665/spring-microservices-architecture).

## Where this sits in the journey

| Release | Tag / branch | What it added |
|---|---|---|
| **v1 Docker Compose** (this branch) | `v1.0-docker-compose`, `release/v1-docker-compose` | Services, discovery, gateway, containers |
| v2 Kubernetes + GitOps | `v2.0-kubernetes-gitops` | kind cluster, Argo CD, CI, security scans, Calico, NetworkPolicies |
| v3 Domain v1 (in progress on `main`) | | Eureka removed for Kubernetes DNS, REST API v1, PostgreSQL next |

v1 itself was built in small steps. Each step has its own tag, so you can check out any of them
and see the system exactly as it was:

| Step | Tag | Commit | What changed | What it taught |
|---|---|---|---|---|
| 1 | `stage-1-localhost` | `738cef3` | order-service calls user-service at `http://localhost:8080` | Two processes talking over HTTP; a hard-coded address only works on one machine |
| 2 | `stage-2-docker` | `2244d9b` | A `Dockerfile` per service; the URL becomes the container name | Containers have their own network; `localhost` inside a container is the container itself |
| 3 | `stage-3-eureka` | `d444145` | Eureka server (`discovery-server`); services register; `@LoadBalanced RestTemplate` calls `http://USER-SERVICE` | Client-side service discovery and load balancing: callers find instances by name, not address |
| 4 | `stage-4-gateway` | `0eec360` | Spring Cloud Gateway on 8085 routes `/order/**` and `/user/**` | One entry point for clients; the services behind it can move or scale |
| 5 | `v1.0-docker-compose` | `dd68f59` | `docker-compose.yml` starts all four; Docker DNS names everywhere | Orchestrating several containers with one command, and Docker's built-in DNS |

```bash
git checkout stage-3-eureka     # look at the system as it was after step 3
git checkout release/v1-docker-compose
```

## Architecture

```
                        client (browser / curl)
                                 |
                                 v   :8085
                        +-----------------+
                        |   api-gateway   |  /order/** -> lb://ORDER-SERVICE
                        |  (Cloud Gateway)|  /user/**  -> lb://USER-SERVICE   (StripPrefix=1)
                        +-----------------+
                          |            |
                          v :8081      v :8080
                 +---------------+  +---------------+
                 | order-service |->| user-service  |   order-service calls http://USER-SERVICE/user
                 +---------------+  +---------------+   through a @LoadBalanced RestTemplate
                          \            /
              register + fetch registry (every 30 s)
                            \        /
                             v      v   :8761
                        +-----------------+
                        |     eureka      |   registry of name -> instance addresses
                        | (discovery-srv) |   dashboard: http://localhost:8761
                        +-----------------+

        all four containers share one Compose network; Docker DNS resolves "eureka" etc.
```

**How a request flows.** `GET http://localhost:8085/order/order`:
1. The gateway matches `/order/**`, strips the first segment (`/order`), and looks up
   `ORDER-SERVICE` in its copy of the Eureka registry.
2. order-service (`GET /order`) asks *its* copy of the registry for `USER-SERVICE`, picks an
   instance (Spring Cloud LoadBalancer, round robin), and calls `GET /user`.
3. user-service answers `Response from USER-SERVICE running on port: 8080`, and order-service
   wraps it: `Order created for -> ...`.

| Service | Port | Endpoint | Spring Boot |
|---|---|---|---|
| discovery-server (Eureka) | 8761 | dashboard `/` | 4.0.2, Spring Cloud 2025.1.0 |
| user-service | 8080 | `GET /user` | 4.0.2 |
| order-service | 8081 | `GET /order` (calls user-service) | 4.0.2 |
| api-gateway | 8085 | `/order/**`, `/user/**` | 3.2.5, Spring Cloud 2023.0.1 |

## Run it

Requires Java 21, Maven, Docker with Compose. The Dockerfiles copy a JAR that is already built
(`COPY target/*.jar`), so build each service first:

```bash
for s in discovery-server user-service order-service api-gateway; do
  (cd "$s" && ./mvnw -q -DskipTests package)
done
docker compose up --build
```

Then:

```bash
curl localhost:8085/order/order    # through the gateway -> order -> user
curl localhost:8085/user/user      # through the gateway -> user
open http://localhost:8761         # Eureka dashboard: which instances are registered
```

Services register about 30 s after they start. Until then the gateway answers `503`.

## Known limitations (why v2 exists)

These are the real problems this version had. Each one was fixed later, on `main`:

| Problem in v1 | Consequence | Fixed in v2 by |
|---|---|---|
| Versions picked per service (gateway on Boot 3.2.5, the rest on 4.0.2, three Spring Cloud trains) | Same library at different versions; upgrades one by one | Shared parent POM (ADR-0001) |
| `management.endpoints.web.exposure.include=*` on the gateway, the only public service | `heapdump`, `env` and `configprops` readable by anyone | Expose `health` and `info` only, plus a test (PR #12) |
| JAR built on the laptop, then copied into a JDK image running as root | "Works on my machine" builds; a large image running as root | Multi-stage Dockerfile: tests run inside the build, JRE-only runtime, non-root user |
| No tests beyond `contextLoads`, no CI | Nothing stopped a broken change | Unit, web, integration and routing tests; GitHub Actions on every PR |
| No dependency or vulnerability tracking | Old Tomcat, Jackson and FreeMarker with critical CVEs | Renovate, Trivy and gitleaks as merge gates (ADR-0002, ADR-0003) |
| `depends_on` only orders start-up, no health checks | Calls fail while Eureka registrations are still missing | Kubernetes readiness probes |
| One copy of everything, one machine | No self-healing, no rolling updates | Kubernetes Deployments and Services, Argo CD GitOps |
| `GET /order` *creates* an order; `/order/order` doubled paths | Unsafe method; confusing URLs | REST API v1 (v3, ADR-0007) |

## Original notes

The README written at step 1 (two services, before Eureka and the gateway) is kept as it was in
[`docs/stage-1-original-README.md`](docs/stage-1-original-README.md).
