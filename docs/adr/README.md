# Architecture Decision Records

An ADR records one significant decision: the problem, the options we considered, what we chose, and what it costs us. ADRs are short, numbered, and never deleted. If a decision changes, a new ADR supersedes the old one, and the old one stays as history.

**Why we keep them.** Six months from now, "why is it like this?" should have a written answer, not a guess. New team members (and future you) read the log instead of re-arguing settled questions.

**When to write one.** Write one when a choice is hard to reverse, affects more than one service, or had real alternatives. Examples: a build structure, a database, a messaging system, a security policy.

**Format.** Copy [`template.md`](template.md). Status is one of `Proposed`, `Accepted`, `Superseded by ADR-NNNN` or `Deprecated`.

| ADR | Title | Status | Date |
|---|---|---|---|
| [0001](0001-shared-parent-pom.md) | One shared Maven parent POM for all services | Accepted | 2026-10-21 |
| [0002](0002-renovate-dependency-updates.md) | Automated dependency updates with Renovate | Accepted | 2026-10-21 |
| [0003](0003-ci-security-scanning.md) | Vulnerability and secret scanning as CI merge gates | Accepted | 2026-10-21 |
| [0004](0004-calico-cni.md) | Calico as the lab's CNI, installed by the bootstrap script | Accepted | 2026-10-22 |
| [0005](0005-shop-default-deny-network-policies.md) | Default-deny NetworkPolicies for the shop namespace | Accepted | 2026-10-22 |
| [0006](0006-kubernetes-dns-service-discovery.md) | Kubernetes DNS for service discovery; Eureka removed | Accepted | 2026-10-22 |
| [0007](0007-rest-api-conventions.md) | REST API conventions: /api/v1, plural resources, problem JSON | Accepted | 2026-10-22 |
