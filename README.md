# Spring microservices lab

A learning lab that builds a small shop backend the way a production team would: Spring Boot
services, Docker images, Kubernetes (kind), GitHub Actions CI and Argo CD GitOps.

**Status: early.** The platform pipeline works end to end; the services are still placeholders
with no domain logic or database. It is not production-ready. Work follows a 10-phase plan
(below); each phase ends with an automated exit check.

## What runs today

```
localhost:80 -> ingress-nginx -> api-gateway -> order-service -> user-service
                                     (service discovery: Eureka)
```

| Service | Port | What it does today |
| --- | --- | --- |
| api-gateway | 8085 | Routes `/order/**` and `/user/**`; exposes only `/actuator/health` and `/actuator/info` |
| order-service | 8081 | `GET /order` calls user-service (1 s connect / 2 s read timeouts) |
| user-service | 8080 | `GET /user` returns a placeholder string |
| discovery-server | 8761 | Eureka registry |

## Repository layout

| Path | Contents |
| --- | --- |
| `*/` (one folder per service) | Spring Boot service, multi-stage `Dockerfile` (tests run during the image build) |
| `.github/workflows/ci.yml` | CI: build and test every service on PRs; on `main` also tag `<service>:<sha7>`, load into kind, clean up old images |
| `k8s/kind-cluster.yaml` | Local cluster: 1 control-plane + 2 workers, ports 80/443 mapped to the laptop |
| `k8s/apps/` | Application manifests (namespace `shop`); image tags pinned in `kustomization.yaml` |
| `k8s/platform/` | ingress-nginx and Argo CD, deployed by Argo CD itself |
| `k8s/argocd/` | App-of-apps: `root.yaml` (applied once) and the child Applications |
| `scripts/lab-cleanup.sh` | Deletes old CI images (keeps running, pinned and last 3 main builds) |
| `docker-compose.yml` | Legacy local setup from before Kubernetes; not maintained |

## Run the lab

Prerequisites: Docker, kind, kubectl.

```bash
kind create cluster --config k8s/kind-cluster.yaml
kubectl create namespace argocd
kubectl apply -n argocd --server-side -k k8s/platform/argocd   # Argo CD
kubectl apply -f k8s/argocd/root.yaml                          # Argo CD deploys everything else from Git
curl http://localhost/order/order
```

The app images are only in kind's node caches today, so a brand-new cluster cannot pull them yet.
A local registry (phase 2) fixes that.

## How a change ships

1. Branch, commit, open a PR. `main` is protected: PR required, 4 build checks must pass,
   review conversations must be resolved.
2. CI builds and tests each service in parallel on self-hosted runners.
3. After merge, CI builds `<service>:<sha7>` and loads it into the cluster.
4. A PR that changes the tag in `k8s/apps/kustomization.yaml` is the deploy request; Argo CD
   syncs the cluster to `main`.

## Roadmap

| Phase | Goal |
| --- | --- |
| 0 | Close the actuator exposure on the gateway; correct this README |
| 1 | Engineering foundation: shared parent POM, CI stages, static analysis, security scans, ADRs |
| 2 | Reproducible platform: local registry, one-command lab rebuild, Calico CNI |
| 3 | Domain v1: user, catalog, order on PostgreSQL; hexagonal design; contract tests |
| 4 | Events: Kafka, payment, notification, outbox, saga |
| 5 | Delivery automation: tag-bump PRs, post-deploy tests, automatic rollback |
| 6 | Observability and performance: OpenTelemetry, Prometheus, Grafana, k6, SLOs |
| 7 | Edge and security: Gateway API, JWT auth, rate limits, Sealed Secrets |
| 8 | Caching and scaling: Redis, HPA, read replicas |
| 9 | Cloud and infrastructure as code: Terraform, managed Kubernetes |
