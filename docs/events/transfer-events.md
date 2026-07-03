# Transfer Events (Redpanda / Kafka API)

Source of truth for the two topics exchanged between transfer and account services. Both services generate DTOs from these definitions. JSON serialization (Spring Kafka `JsonSerializer`/`JsonDeserializer`, trusted packages configured).

Every event carries a unique `messageId` (uuid) used for inbox dedup. Money fields are integers in minor units (VND: 1 = 1đ), mapped to `long` — no float precision loss. Accounts are referenced by their public `accountRef` (12-digit string), never the internal account UUID.

## Topic: transfer.requested

- Producer: transfer service (via outbox relay)
- Consumer: account service (group `account-service`)
- Partition key: `fromAccountRef` → sequential processing per account, avoids balance races.

```json
{
  "messageId": "uuid",
  "transferId": "uuid",
  "fromAccountRef": "100000000001",
  "toAccountRef": "200000000001",
  "amount": 100000,
  "currency": "VND",
  "requestedAt": "2026-07-03T10:00:00Z"
}
```

## Topic: transfer.result

- Producer: account service (via outbox relay)
- Consumer: transfer service (group `transfer-service`)
- Partition key: `transferId`.

```json
{
  "messageId": "uuid",
  "transferId": "uuid",
  "status": "COMPLETED",
  "reason": null,
  "processedAt": "2026-07-03T10:00:01Z"
}
```

- `status`: `COMPLETED` | `FAILED`
- `reason`: `null` when COMPLETED; `INSUFFICIENT_FUNDS` | `ACCOUNT_NOT_FOUND` when FAILED.

## Delivery semantics

- **Publishing**: both producers write the event to an `outbox` table in the same DB transaction as the business change, then a polling relay (`@Scheduled`, `SELECT ... FOR UPDATE SKIP LOCKED`) publishes and marks `SENT`. Polling relay, not CDC/Debezium (out of scope). See each service's "Publishing: outbox relay" section: [transfer](../services/transfer-service.md), [account](../services/account-service.md).
- **At-least-once**: a crash between broker ack and the `SENT` update causes a re-send. Both consumers dedup on `messageId` via their `processed_messages` (inbox) table. Outbox + inbox are a pair.

## Topic config (demo)

- 1 partition per topic (single-node Redpanda dev mode).
- Auto-create disabled; topics created explicitly via `rpk` (see Phase 1).
