# ADR-0003: Vulnerability and secret scanning as CI merge gates

- **Status:** Accepted
- **Date:** 2026-10-21
- **Related:** PR #19, ADR-0002

## Context
- **Vulnerabilities were only found by chance.** On 2026-10-21 each image had 4–12 critical CVEs, and they were found only because someone ran a scan by hand. CI never looked inside images.
- **Leaked secrets would be permanent.** The repository is public, so a committed secret stays readable in git history forever, even after a later commit removes it.
- **Constraints:**
  - Tools must be free.
  - CI runs on 4 self-hosted runners on one 8 GB laptop.
  - In March 2026 Trivy's own release tags were hijacked to ship malicious code (CVE-2026-33634).

## Options considered
| Option | Pros | Cons |
|---|---|---|
| A. Scan manually now and then | No CI cost | Same gap as before |
| B. GitHub-native (Dependabot alerts, secret scanning) | Free on public repos | Alerts don't block merges by themselves; doesn't scan the built image's OS layers |
| C. Trivy (images) + gitleaks (git history) in CI as merge gates | Free, open source, scan exactly what we ship, block merges | Adds CI time and a ~3 GB vulnerability DB on the laptop |
| D. Commercial scanners (Snyk etc.) | Richer dashboards | Not free |

## Decision
Option C.

**Trivy:**
- Scans each image after build and before push.
- Lists HIGH and CRITICAL findings that have a fix.
- Fails the build on any CRITICAL that has a fix (`--ignore-unfixed`), so every failure has a known remedy.

**gitleaks:**
- Scans the full git history.
- Fails on any finding; `--redact` keeps the secret itself out of logs.

**Both:**
- Scanner images are pinned by tag and digest, and Renovate updates them.
- `secret scan (gitleaks)` is a required check in the `protect-main` ruleset, alongside the 4 builds.

For the laptop, one shared Trivy DB is updated under a file lock, and scans read it in parallel with `--skip-db-update` and `--cache-backend memory`. The first design used one DB per runner and overloaded the machine for 15 minutes; the fix brought a full build plus scan to about 80 seconds per service.

## Consequences
- **Positive:**
  - A vulnerable image can't reach the registry.
  - A leaked secret can't be merged.
  - Findings are visible on every run.
- **Negative / costs:**
  - Roughly 30–40 s extra per build.
  - A new CVE published against an unchanged image can suddenly fail CI. That is intended: the fix is a Renovate or override bump.
  - About 3 GB of disk for the shared DB.
- **Not covered yet:**
  - Static code analysis (SAST, e.g. CodeQL or Semgrep) and SBOM or image signing (Syft, cosign).
  - HIGH findings in the base image's `pebble` binary are reported only, and should clear through Renovate's digest-pinning PRs.
  - These belong to later phases (supply-chain hardening).
