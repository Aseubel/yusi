# Yusi Account Deletion Privacy Audit Runbook

This runbook is a deployment-only rehearsal. It is not a local test recipe and
must not be executed against a production account or the production namespace.

## Evidence Classes

| Class | Meaning | Local status |
| --- | --- | --- |
| `application-invariant-only` | H2 verifies application SQL ordering, residual counts, and orphan queries. | Allowed locally |
| `mock-contract-only` | Mockito verifies Milvus request shape and Redis/OSS adapter calls. | Allowed locally |
| `deployment-only` | A disposable MySQL 8, Milvus, Redis, and OSS namespace was queried after the operation. | Never inferred locally |

Every rehearsal record uses an opaque run reference, operation categories,
counts, timestamps, exception types, and status. It must not contain user ID
lists, query text, message/content fields, tokens, Redis values, or complete
object keys.

## Disposable Rehearsal

1. Create one isolated namespace with one synthetic target and one control
   account. Use only fixed `fixture-*` identifiers and non-natural-language
   sentinel values.
2. Freeze target writes and scheduled workers before the request starts.
3. Record the opaque run reference and initial row/key/vector/object counts.
4. Submit the administrator deletion request through the unchanged admin route.
5. Require a terminal deletion status. A retry status is a blocked rehearsal,
   not a successful deletion.

## Deployment-Only Checks

### MySQL 8

- Confirm transaction isolation and write freeze behavior while a deletion is
  running.
- Query every owner/participant table covered by the privacy invariant and
  record only classified residual counts.
- Run the orphan queries for graph endpoints/evidence, match/connection/event
  references, task/run correlations, product-event scopes, and audit scopes.
- Confirm control rows remain and shared-room data was reduced according to the
  approved policy.

### Milvus

- Query all three collections: embedding, mid-term memory, and match profile.
- Wait for the configured consistency/flush boundary, then verify target
  metadata and profile primary-key counts are zero.
- Repeat the query after segment compaction or the platform's equivalent
  visibility boundary. A delete API response alone is insufficient evidence.

### Redis

- Check refresh-token and device-token families for the target, and verify
  revoked access tokens remain only for their approved TTL.
- Check usage hashes by field, violation counters, LangChain cache, and the
  explicitly allow-listed business cache keys.
- Confirm no global model runtime/configuration key was deleted and no worker
  repopulated a target key after the freeze was released.

### OSS

- Check image, audio, attachment, and multipart temporary-object inventories.
- Verify delete markers, historical versions, lifecycle replicas, and any
  configured cross-region destination.
- If an object has another user's live image mapping, record it as retained by
  shared-reference policy and verify no target mapping remains.

### Workers and Backups

- Verify embedding, lifegraph, matching, usage, trace, and notification workers
  cannot recreate target rows or external copies after deletion.
- Re-run the deletion after a controlled retry/restart to confirm idempotency.
- Execute the backup/restore rehearsal's tombstone and retention checks. Backup
  artifacts and third-party/provider copies remain separate compliance evidence.

## RTO Record

```text
rehearsalRunRef: <opaque-reference>
environment: <disposable-preprod-namespace>
startedAt: <ISO-8601>
deletionRequestAcceptedAt: <ISO-8601>
recoveryOrDeletionCompletedAt: <ISO-8601>
evidenceClass: deployment-only
mysqlResidualCount: <count>
orphanCount: <count>
milvusResidualCount: <count>
redisResidualCount: <count>
ossResidualCount: <count>
controlDataPreserved: <true|false>
workerRecreationCheck: <pass|blocked>
backupReplicaCheck: <pass|blocked|not-run>
integrityResult: <pass|blocked>
operatorRef: <opaque-reference>
rollbackOrBlockReason: <fixed-category-or-empty>
```

Roadmap L634 remains unchecked until this record, the real dependency checks,
and the backup/third-party retention review are independently accepted.
