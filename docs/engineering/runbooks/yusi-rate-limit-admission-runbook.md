# Yusi Rate-Limit and Admission Runbook

## Scope

This runbook records the application contract for request rate limiting and model
budget admission. Values below are initial values, pending production tuning.
Local H2 and Mockito checks prove only application invariants and mock contracts;
they do not prove distributed enforcement or vendor quota behavior.

All operational evidence uses only operation, result, failure category, count,
window, timestamp, and deployment status. Do not record request subjects,
request bodies, search terms, credentials, provider payloads, or storage keys.

## Application Defaults

| Operation family | Dimension | Count | Window | Status |
| --- | --- | ---: | ---: | --- |
| Memory fusion | USER | 2 | 600 seconds | Initial value, pending production tuning |
| Situation-room narrative submit | USER | 3 | 600 seconds | Initial value, pending production tuning |
| Embedding full sync | USER | 1 | 3600 seconds | Initial value, pending production tuning; super-admin only |
| Image URL signing | USER | 60 | 60 seconds | Initial value, pending production tuning |
| Image delete | USER | 30 | 60 seconds | Initial value, pending production tuning |
| Image batch delete | USER | 5 | 60 seconds | Initial value, pending production tuning |
| Diary key re-encryption | USER | 2 | 600 seconds | Initial value, pending production tuning |
| Ordinary authenticated writes | USER | 10-30 | 60 seconds | Initial value, pending production tuning |
| Administrative writes | USER | 2-20 | 60-600 seconds | Initial value, pending production tuning |

The complete endpoint contract is maintained by
`RateLimitCoverageContractTest`. A change to a controller write mapping must
update its explicit annotation and the corresponding review manifest in the
same change.

## Failure Semantics

- Redis is the distributed limiter when available.
- Redis failure uses a bounded local bucket; it never becomes unlimited pass-through.
- A missing subject HMAC deployment secret fails subject-scoped limiting closed.
- A rejected request returns only the fixed `RATE_LIMIT_EXCEEDED` code and fixed
  public message.
- `rate_limited_total` and `budget_denied_total` remain separate counters.
- Allowed metric labels are exactly `tool`, `operation`, `result`, and
  `failure_category`. Subject values, request values, model/provider names, and
  dimensions are not metric labels.

## Deployment-Only Checks

The following checks are intentionally `NOT_RUN` by local Maven tests and must
be performed in an isolated deployment environment before changing any
production threshold or claiming rollout readiness.

| Check | Required evidence | Local status |
| --- | --- | --- |
| Concurrent HTTP writes | Per-operation allowed/rejected counts over a fixed window; no request data | NOT_RUN |
| SSE concurrency and reconnects | Connection and rejection counts; stream behavior before and after response commit | NOT_RUN |
| Multipart and batch byte limits | Gateway byte/concurrency rule counters and rejection classification | NOT_RUN |
| Redis multi-replica enforcement | Same operation window observed consistently across replicas | NOT_RUN |
| Redis outage and recovery | Bounded-local fallback, recovery transition, and no unlimited pass-through | NOT_RUN |
| Gateway enforcement | Ingress byte, connection, and burst rules for routes outside method annotations | NOT_RUN |
| Provider quota calibration | Production request/token observations mapped to admission dimensions | NOT_RUN |
| Milvus full-sync operation | Super-admin allowlist, one-run window, and completion count | NOT_RUN |
| OSS signing and deletion | Batch size/byte controls and operation counts | NOT_RUN |
| Management port `20611` | Network allowlist and no public exposure of management routes | NOT_RUN |
| WebSocket and gRPC entry points | Route-specific concurrency and byte controls | NOT_RUN |

## Evidence Template

Record one row per operation and fixed observation window:

```text
deployment_status=NOT_RUN
operation=<fixed-operation-name>
window_seconds=<integer>
allowed_count=<integer>
rejected_count=<integer>
failure_category=<fixed-category>
observed_at=<UTC timestamp>
rollback_reference=<change or configuration reference>
```

Use only the fixed categories `limit_exceeded`, `dependency`, and `unknown`
for request limiting, and
`admission_store_unavailable`, `reservation_conflict`, `limit_exceeded`, and
`unknown` for model admission. Threshold changes require a recorded before /
after observation and a rollback reference; no local mock result may be used as
the production observation.
