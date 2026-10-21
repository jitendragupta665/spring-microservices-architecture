# ADR-0001: One shared Maven parent POM for all services

- **Status:** Accepted
- **Date:** 2026-10-21
- **Related:** PR #14 (implementation), PR #15 (rollout)

## Context
Each service picked its own Spring Boot and Spring Cloud versions in its own `pom.xml`, and nothing kept them aligned. Measured on 2026-10-21 with `mvn dependency:list` and Trivy:

- **api-gateway:** Spring Boot 3.2.5, whose free (OSS) support ended Dec 2024, with Spring Cloud 2023.0.1.
- **The other three services:** Spring Boot 4.0.2, on two different Spring Cloud trains (2025.1.0 and 2025.1.1).
- **Library drift:** 61 of the 105 libraries shared between services resolved to different versions. For example, Spring Framework was 6.1.6 in the gateway and 7.0.3 elsewhere.
- **Known vulnerabilities:** 4–12 critical and 36–46 high CVEs per running image.

Every upgrade meant editing 4 files, and forgetting one is how the gateway fell behind.

## Options considered
| Option | Pros | Cons |
|---|---|---|
| A. Keep per-service versions | No change; services fully independent | Drift and CVEs pile up; 4x upgrade work |
| B. Shared **BOM** only (each service imports it) | Services keep their own parent; aligns library versions | Plugin config and Java version still duplicated per service; Spring Boot's own parent features lost or re-declared |
| C. Shared **parent POM** inheriting `spring-boot-starter-parent` | One file sets Java, Boot, Cloud and plugin defaults; security overrides in one place | Services upgrade together; one service's breakage blocks the others; Docker builds need the parent file |
| D. Maven multi-module monorepo (root aggregator) | One `mvn verify` builds everything | Larger restructure of folders, CI and Docker builds; not needed at 4 services |

## Decision
Option C: `shop-parent/pom.xml` inherits Spring Boot 4.1.1's parent and imports the Spring Cloud 2025.1.3 BOM. It also holds patch-level security overrides (Tomcat, Jackson, FreeMarker). Every service inherits it and declares no versions of its own. Docker builds receive it as a second build context (`--build-context parent=./shop-parent`).

Why not D: a monorepo build is the natural next step if service count grows. At 4 services it would add restructuring without solving a current problem.

## Consequences
- **Positive:**
  - An upgrade is a one-line change.
  - All services run identical library versions.
  - After the change, 0 critical and 0 high CVEs remain in the Java libraries.
  - Security fixes apply everywhere at once.
- **Negative / costs:**
  - All services move together. Breaking changes, like the Gateway starter rename in this upgrade, must be fixed in the same PR.
  - Docker, CI and compose builds need the extra build context.
- **Follow-ups:**
  - Remove each security override once Spring Boot's BOM ships that version or newer. ADR-0002's Renovate rules surface this.
  - Revisit option D if the service count grows past about 8.
