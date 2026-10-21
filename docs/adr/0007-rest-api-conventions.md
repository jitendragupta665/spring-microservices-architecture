# ADR-0007: REST API conventions: /api/v1, plural resources, problem JSON

- **Status:** Accepted
- **Date:** 2026-10-22
- **Related:** ADR-0006

## Context
The first API had placeholder endpoints that grew by accident:
- **Doubled paths.** The gateway stripped `/order` from `/order/order` and the service added
  `/order` back. Each URL said the same word twice, and the path seen inside differed from the
  one seen outside.
- **Wrong method.** `GET /order` *created* an order. GET must be safe to repeat; browsers,
  caches, crawlers and automatic retries re-send it.
- **No contract.** Responses were plain text, there was no version, and every failure was a
  generic 500.

## Decision
1. **Resources are plural nouns under a version:** `/api/v1/users/{id}`, `/api/v1/orders`,
   `/api/v1/orders/{id}`. A breaking change becomes `/api/v2/...`, and v1 clients keep working.
2. **Methods follow HTTP semantics:** GET reads (safe and idempotent), POST creates and answers
   `201 Created` with a `Location` header.
3. **Paths are not rewritten.** The gateway forwards `/api/v1/...` unchanged (no `StripPrefix`),
   so the same path appears in client calls, gateway logs and service logs.
4. **JSON in and out.** Bodies are Java records; request bodies are validated with Bean
   Validation (`@Valid`).
5. **Errors are RFC 9457 problem details** (`application/problem+json`:
   `type`, `title`, `status`, `detail`), mapped in one `@RestControllerAdvice` per service. The
   status says who must act:

   | Status | Meaning | Client action |
   |---|---|---|
   | 400 | Body or parameter invalid | Fix the request |
   | 404 | That resource does not exist | Do not retry |
   | 422 | Request is valid, but a rule rejects it (unknown user) | Fix the data |
   | 503 + `Retry-After` | A dependency is down | Retry later |

   Internal details (host names, stack traces) go to the service log, never into the response.

## Consequences
- **Good:** clients get a predictable contract. Errors tell them whether to fix the request or
  retry. Contract tests (Phase 3) have something precise to check.
- **Bad:** every existing caller of `/order/order` and `/user/user` must move. There are no
  external clients yet, so the old paths are removed instead of kept alongside the new ones.
- **Later:** an OpenAPI description generated from the controllers, and idempotency keys for
  `POST /orders`, so a retried create does not place two orders.
