# ADR-0005: Default-deny NetworkPolicies for the shop namespace

- **Status:** Accepted; the Eureka rules were removed by ADR-0006 (Eureka retired)
- **Date:** 2026-10-22
- **Related:** ADR-0004 (Calico, which enforces these rules), `k8s/apps/network-policies.yaml`

## Context
Kubernetes networking is flat by default: any pod in any namespace can open a connection to any other pod, and any pod can reach the internet. For the shop that means:
- a compromised `user-service` could call `order-service` or `api-gateway` directly, skipping the gateway;
- any pod in another namespace could hit any shop service on its container port, bypassing ingress;
- any shop pod could send data anywhere on the internet (exfiltration) or pull a second-stage payload.

The real call graph is small and fixed:

```
ingress-nginx -> api-gateway -> order-service -> user-service
                     |               |               |
                     +------> eureka <---------------+
all shop pods -> CoreDNS (kube-system) on port 53
```

## Options considered
| Option | Pros | Cons |
|---|---|---|
| A. No policies (status quo) | Nothing to maintain | Flat network; one compromised pod reaches everything |
| B. Namespace isolation only (allow all within `shop`, deny from outside) | One small policy | Lateral movement inside the namespace stays open |
| C. Default deny + one allow-list policy per service | Least privilege; each policy documents a real dependency; standard Kubernetes API, portable across CNIs | A new call between services needs a policy change, or it times out |
| D. Service mesh (Istio/Linkerd) with mTLS identity policies | Identity-based, L7-aware, encrypted | Large extra component; too much for this stage |

## Decision
Option C.
- `default-deny-all` selects every pod and allows nothing, for both directions.
- `allow-dns-egress` lets every pod reach CoreDNS on UDP/TCP 53.
- One policy per service lists exactly who may call it (ingress) and whom it may call (egress), by pod label and port.
- Only the ingress-nginx controller pods may reach `api-gateway`. Eureka has no egress, since it does not register with itself.

Rules are written as separate ingress and egress halves. In Kubernetes a connection must be allowed by the sender's egress **and** the receiver's ingress.

## Consequences
- **Good:** a compromised pod can only reach its declared dependencies. Internet egress from the shop is blocked. The policy file is a living, enforced map of the architecture.
- **Bad:** adding a call (for example order-service calling a new payment-service) needs a policy change in the same PR, or the call times out silently. Debugging starts with `kubectl -n shop get networkpolicy`.
- **Verified live (2026-10-22):** 6 allowed paths open, 5 denied paths closed (including internet egress), an outsider pod in `default` cannot reach `api-gateway`, 40/40 requests through ingress return 200, all pods Ready with 0 restarts.
- **Next:** mTLS and identity-based policy (Option D) when a service mesh enters the roadmap.
