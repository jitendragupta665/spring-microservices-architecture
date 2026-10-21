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
| api-gateway | 8085 | Routes `/order/**` and `/user/**`. Images built from PR #12 onward expose only `/actuator/health` and `/actuator/info`; check the deployed tag in `k8s/apps/kustomization.yaml` |
| order-service | 8081 | `GET /order` calls user-service (1 s connect / 2 s read timeouts) |
| user-service | 8080 | `GET /user` returns a placeholder string |
| discovery-server | 8761 | Eureka registry |

## Repository layout

| Path | Contents |
| --- | --- |
| `*/` (one folder per service) | Spring Boot service, multi-stage `Dockerfile` (tests run during the image build) |
| `shop-parent/pom.xml` | Shared Maven parent: the ONE place that sets Java, Spring Boot, Spring Cloud and security-patched library versions for every service. Docker builds receive it as a second build context (`--build-context parent=./shop-parent`) |
| `.github/workflows/ci.yml` | CI: build and test every service on PRs; on `main` also push `<service>:<sha7>` to the local registry and clean up old images |
| `k8s/kind-cluster.yaml` | Local cluster: 1 control-plane + 2 workers, ports 80/443 mapped to the laptop, no default CNI |
| `k8s/apps/` | Application manifests (namespace `shop`); image tags pinned in `kustomization.yaml` |
| `k8s/platform/` | Calico (installed by `lab-up.sh`), ingress-nginx and Argo CD (deployed by Argo CD itself) |
| `k8s/argocd/` | App-of-apps: `root.yaml` (applied once) and the child Applications |
| `scripts/lab-up.sh`, `scripts/lab-down.sh` | Rebuild / delete the whole lab with one command |
| `scripts/registry-up.sh` | Local image registry `kind-registry` on `localhost:5001`, wired into every kind node |
| `scripts/lab-cleanup.sh` | Deletes old CI images from the host, the nodes and the registry (keeps running, pinned and last 3 main builds) |
| `docker-compose.yml` | Legacy local setup from before Kubernetes; not maintained |

## Run the lab

Prerequisites: Docker, kind, kubectl, git, curl.

```bash
scripts/lab-up.sh     # build everything from Git, with a timing per step
scripts/lab-down.sh   # delete the cluster (the registry and its images are kept)
```

`lab-up.sh` is idempotent: re-running it on a running lab only verifies. What it does, in order:

| Step | Why it is in the script and not in Argo CD |
| --- | --- |
| kind cluster (`k8s/kind-cluster.yaml`, default CNI disabled) | Argo CD needs a cluster to run in |
| Calico CNI (`k8s/platform/calico`) | No pod (Argo CD included) can start before pod networking exists |
| Local registry (`scripts/registry-up.sh`) | Nodes must be able to pull images before anything deploys |
| Images for the pinned tag | Built from that commit only if the registry volume does not have them |
| Argo CD + `k8s/argocd/root.yaml` | Bootstrap; from here Argo CD deploys ingress-nginx and the shop from Git |
| Smoke test | 5 consecutive `200` from `http://localhost/order/order` |

Disaster-recovery drill (2026-10-21, laptop with 8 GB RAM): `lab-down.sh` then `lab-up.sh` brought
`/order/order` back in **523 s**: cluster 53 s, Calico + nodes 183 s (image pulls),
registry 3 s, images 0 s (already in the registry volume), Argo CD 131 s, GitOps sync 115 s,
smoke test 32 s.

Argo CD UI: `kubectl --context kind-lab -n argocd port-forward svc/argocd-server 8080:80`, then
http://localhost:8080, user `admin`, password from
`kubectl --context kind-lab -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d`
(a new one after every rebuild).

## How a change ships

1. Branch, commit, open a PR. `main` is protected: PR required, 4 build checks must pass,
   review conversations must be resolved.
2. CI builds and tests each service in parallel on self-hosted runners.
3. After merge, CI builds `<service>:<sha7>` and pushes it to the local registry.
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
