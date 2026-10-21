# ADR-0004: Calico as the lab's CNI, installed by the bootstrap script

- **Status:** Accepted
- **Date:** 2026-10-22
- **Related:** PR #10 (Argo CD NetworkPolicies removed), PR #24 (`lab-up.sh` + Calico), this PR (policies restored)

## Context
A CNI gives every pod an IP, routes traffic between nodes and enforces NetworkPolicy. kind's default CNI, kindnet, enforces NetworkPolicy through kube-network-policies:
- The first packet of each new connection to a policy-selected pod is sent from the kernel to a userspace agent over NFQUEUE.
- The agent decides, and the verdict is cached for the connection.
- The agent learns about pods through containerd's NRI plugin interface.

On this WSL2 host the path wedged three times. containerd timed out the NRI plugin, and the queue stopped being read while still bound, so the bypass flag never applied. Every new connection of a policy-selected pod hung, DNS included, and Argo CD lost GitHub ("Sync: Unknown"). The workaround (PR #10) deleted Argo CD's 7 NetworkPolicies, leaving the cluster with no policies at all.

## Options considered
| Option | Pros | Cons |
|---|---|---|
| A. Keep kindnet, no NetworkPolicies | Simplest, smallest | No network isolation at all; can't learn or test policies |
| B. Calico (manifest install) | Most widely used open-source CNI; policies compiled into kernel rules ahead of time by Felix, so no per-connection userspace round trip, and rules stay in force while the agent restarts | ~400 MB image per node (slower rebuilds); VXLAN mode needed on WSL2 |
| C. Cilium (eBPF) | Strong eBPF dataplane, L7 policies, Hubble observability | Heavier, more kernel feature requirements on WSL2; more to learn at once |
| D. Calico via the Tigera operator | The production-recommended install path | Operator plus CRDs add complexity a lab doesn't need yet |

## Decision
Option B. Calico v3.32.2 runs from the upstream manifest, vendored unchanged with its sha256 recorded. Lab patches are applied with Kustomize:
- **Encapsulation:** VXLAN, built into the WSL2 kernel, instead of BGP + IP-in-IP, whose kernel support is a loadable module.
- **Pod pool:** `192.168.0.0/16`, matching `podSubnet` in `k8s/kind-cluster.yaml`.
- **Health checks:** BIRD checks removed, since BGP is not used.

`scripts/lab-up.sh` installs it, not Argo CD. No pod, Argo CD included, can start before the CNI exists, so the CNI belongs to the bootstrap layer (layer 0) with the cluster and the registry.

## Consequences
- **Positive:**
  - NetworkPolicy is enforced. Verified in the lab: a test pod is BLOCKED by a deny-all ingress policy.
  - Argo CD's 7 upstream policies are restored, and Argo CD stays healthy under repeated hard refreshes and a restart of every Calico agent.
  - Isolation between shop services becomes possible.
- **Negative / costs:**
  - A rebuild spends ~3 minutes pulling Calico images (DR drill: 183 s of 523 s).
  - One more component to upgrade by hand: replace `calico.yaml`, keep the patches.
- **Follow-ups:**
  - Default-deny NetworkPolicies for the `shop` namespace.
  - A local pull-through mirror for public images to shorten rebuilds.
  - Revisit option C or D when the lab moves to a cloud cluster (Phase 9).
