# ADR-0002: Automated dependency updates with Renovate

- **Status:** Accepted
- **Date:** 2026-10-21
- **Related:** PR #16, ADR-0001

## Context
ADR-0001 put all versions in one file, but a person still had to notice new releases. That gap is how the gateway sat for almost two years on an end-of-life Spring Boot. New CVEs are published continuously, so "upgrade when someone remembers" means vulnerabilities pile up. Constraint: tools must be free.

## Options considered
| Option | Pros | Cons |
|---|---|---|
| A. Manual upgrades | No tooling | Relies on memory; proven to fail |
| B. GitHub Dependabot | Built into GitHub, free | Weaker grouping and scheduling; can't track plain Maven properties like our security overrides |
| C. Renovate, Mend-hosted free app | Free for all repos; grouping, schedules, Dependency Dashboard, regex managers, digest pinning | Third-party app with repo access (limited to this repo); jobs run about every 4 hours |
| D. Renovate CLI self-hosted in our CI | No third-party app | More setup and maintenance on our runners |

## Decision
Option C, limited to this repository, with the rules in `renovate.json`:

- **Weekly batch:** patch and minor updates are grouped into one PR every Monday before 9am IST.
- **Major upgrades:** each gets its own PR.
- **Spring Boot and Spring Cloud:** they always move together and are never split by update type. A minor or major jump waits for approval on the Dependency Dashboard, because it needs a compatible partner release.
- **Security overrides in `shop-parent`:** tracked by regex managers. Patch bumps flow automatically; larger jumps wait for approval.
- **Docker base images and CI scanner images:** pinned by digest, so a republished image becomes a PR.
- **Security fixes:** skip the schedule, capped at 3 open PRs.

## Consequences
- **Positive:**
  - Upgrades find us; CI tests every one before a human merges it.
  - Image digest refreshes, which fix OS-level CVEs, become visible.
- **Negative / costs:**
  - A steady stream of PRs to review.
  - Each merge still needs a manual deploy (tag bump) until automated delivery in Phase 5.
- **Follow-ups:**
  - If the third-party app becomes a concern, switch to option D.
  - Revisit the schedule once tests cover more behaviour, since auto-merge of patch updates becomes reasonable then.
