# DivineMarketplace async runtime plan

This is the current plan for moving the marketplace from mostly synchronous SQL/menu work to a memory-first, async-safe Paper plugin runtime.

## Core rule

Minecraft/Paper-facing work stays on the main server thread. Plain marketplace data work should move async wherever it does not touch Bukkit/Paper/Vault objects.

## Thread boundaries

### Main thread only

- Inventory click/drag events.
- Reading clicked slots and active menu inventories.
- Opening, closing, or updating chest GUIs.
- Reading or mutating player inventory contents.
- Giving/removing items.
- Sending player-facing Bukkit/Paper interactions.
- Vault/economy calls unless a dependency explicitly guarantees async safety.
- Entity, world, block, scheduler, and plugin-manager interactions.

### Async/plain Java

- SQLite startup reads.
- SQLite queued writes.
- Encoding/decoding plain persisted payloads where no Bukkit object access is needed.
- Sorting/filtering listing snapshots for menu pages.
- Search result preparation from memory snapshots.
- Price recalculation math.
- Market analytics/report generation.
- Market event projection/export work.

## Storage model

### Runtime truth

- SQL loads into memory at startup/reload.
- After startup, normal GUI/command reads use memory/indexes only.
- SQL is durability, recovery, reporting, migration, and admin diagnostics.
- Normal menu actions should never query SQL.

### SQLite worker

- Keep WAL enabled.
- Use one controlled SQLite write pipeline because SQLite still has one writer at a time.
- Services should not write directly to SQL from random code paths.
- Old direct append/save/delete methods should be purged or restricted after queued replacements exist.
- Runtime mutations update memory first, then enqueue one write batch.
- One market action should become one SQL transaction-sized batch.

Example purchase batch:

```text
update/delete active listing
upsert seller money claim
upsert buyer item claim
append canonical market event
append/update price/training projection if needed
```

### Flush timing

- Critical state should not wait five minutes.
- Default target: flush every 30-60 seconds, plus threshold-based flushes.
- Purchases/claims/listing mutations may flush sooner by queue size or action count.
- Low-risk analytics/checkpoints can use a slower timer.
- Plugin disable must stop new actions and immediately flush pending batches.
- If an async flush fails after memory accepted an action, do not reload from SQL. Pause marketplace actions, keep memory and the queued mutations, reconnect the SQLite/Hikari datasource, retry a blocking flush, then resume READY only after queued writes are durable.

### Storage recovery

Storage recovery is intentionally small: there is no degraded marketplace mode. The marketplace is either READY, temporarily RECOVERING_STORAGE, or FAILED.

```text
async flush fails
  -> failed mutations are re-queued at the front
  -> runtime enters RECOVERING_STORAGE
  -> commands/menus/schedulers reject mutation work because runtime is not READY
  -> reconnect SQLite/Hikari without rebuilding marketplace memory
  -> retry a blocking flush of the same queued mutations
  -> READY on success, FAILED after bounded retries
```

Rules:

- Do not reload SQL into memory during recovery because SQL may be behind accepted in-memory actions.
- Shutdown still uses the blocking flush path.
- Recovery retries are for rare transient SQLite/Hikari hiccups; persistent failure should fail the marketplace runtime loudly.

### Maintenance gate

Purge/delete/reload/checkpoint/VACUUM work must not race the normal write-behind queue. Treat maintenance as an exclusive storage mode:

```text
request maintenance
  -> stop accepting normal new write batches, or buffer them separately
  -> flush pending write-behind batches
  -> run the maintenance transaction/read/reload/checkpoint through the storage worker
  -> resume normal queued writes
```

Rules:

- Normal queued writes and destructive purge/delete operations must never run concurrently.
- Maintenance should use the same SQLite database configuration, including WAL.
- Reload should be scoped and async: config, menu definitions, prices, or storage diagnostics should have specific command paths.
- Avoid one giant synchronous `/market reload` that rebuilds SQL-backed runtime state on the server thread.

## Snapshot model

Snapshots are for async reading and planning, not blind replacement of runtime memory.

Use this pattern:

```text
authoritative memory
  -> immutable snapshot at version N
  -> async worker calculates result/delta
  -> runtime validates version/current locks
  -> runtime applies delta to authoritative memory
  -> runtime queues SQL write batch
```

Do not use this pattern:

```text
copy memory
mutate copy async
replace current memory blindly
```

Snapshot candidates:

- active listing summaries
- listing ids by category/market key/seller
- item claim ids by owner
- money claim balances by owner
- current price profiles
- recent market events/sales
- search/display DTOs that do not contain live Bukkit objects

Current-memory/lock-only actions:

- buy listing
- cancel listing
- claim item
- claim money
- relist claim
- admin remove/restore/change actions

## Player input and GUI safety

The chest GUI itself is synchronous. The goal is not to async Bukkit inventory code; the goal is to make the synchronous click path very small.

Click path target:

```text
cancel event
check runtime READY
check player/session lock
check logical object lock
snapshot any Minecraft-specific data needed
call service using memory only
queue SQL batch
schedule/open/update GUI on main thread
unlock after the whole action is complete
```

Locks needed:

- Per-player action lock: prevents one player from double-clicking buy/claim/cancel.
- Per-object lock: prevents two players from buying/cancelling/claiming the same logical object at once.

Logical lock keys:

```text
listing:<listingUuid>
item-claim:<claimUuid>
money-claim:<playerUuid>
admin-action:<objectUuid>
```

## Market events and history merge

Short-term TODO:

- Add a canonical `market_events` model/table/store.
- Merge admin history and player market/sale history into one event stream where they describe the same action.
- Keep separate memory projections only when useful for fast GUI reads.
- Stop writing duplicate sales/admin data once event projections cover both views.

Candidate event types:

```text
LISTING_CREATED
LISTING_CANCELLED
LISTING_EXPIRED
SALE_COMPLETED
ITEM_CLAIM_CREATED
ITEM_CLAIM_DELIVERED
MONEY_CLAIM_CREATED
MONEY_CLAIM_PAID
PRICE_RECALCULATED
ADMIN_ACTION
CUSTOM_ITEM_FLAGGED
CUSTOM_ITEM_CONFIRMED
```

## Cleanup/deletion policy

The next cleanup direction is time-based retention instead of storage-size-based retention.

Reason:

- Time-based deletion is easier to reason about.
- It avoids repeated size calculations across tables.
- It is easier for admins to configure.
- It lines up naturally with market-event/history retention windows.

Target:

- Startup/manual cleanup deletes old events/history by age.
- Runtime monitoring can remain cheap and non-destructive.
- VACUUM/checkpoint should remain manual/startup/admin, not frequent gameplay work.
- Cleanup/checkpoint must run behind the storage maintenance gate so the main writer is idle while maintenance is active.

## Patch order

1. ✅ Add and wire player/object action locks for GUI mutation actions.
2. ✅ Add multi-table SQLite write batches so one market action can persist as one transaction.
3. ✅ Add runtime states: LOADING, READY, RECOVERING_STORAGE, FAILED, DISABLING.
4. ✅ Make `/market` and menu clicks reject/soft-fail while runtime is not READY.
5. ✅ Stop store constructors from doing blocking reloads on the main enable path.
6. ✅ Add async bootstrap load: SQL read -> memory hydrate -> READY transition.
7. ✅ Convert purchase/cancel/claim/list/relist/expiration to create queued write batches instead of direct SQL writes for the main player flows.
8. ✅ Purge or restrict stale direct append/save/delete APIs so future code cannot bypass the queue by accident.
9. ✅ Add the storage maintenance gate so purge/delete/checkpoint/reload work is mutually exclusive with the main write-behind worker.
10. ✅ Make `/market reload` targeted and async, starting with config/menu/prices, while keeping WAL and worker coordination intact.
11. ✅ Merge admin history + sale/market history into canonical market events.
12. ✅ Replace storage-size event cleanup with time-based retention windows.
13. ✅ Add optional async page preparation for expensive menu/search pages.
14. ✅ Add storage recovery for rare queued-flush failures without reloading memory from stale SQL.
15. Next: add snapshot APIs / FastUtil-backed indexes if profiling shows hot memory scans.
16. Revisit MySQL/Discord expansion after alpha stability.

## 2026-05-06 Patch: Claim/Listing Write-Batch Conversion

Converted the next high-risk player flows to the memory-first write-behind pattern:

- Listing creation from player main hand now mutates listing/admin memory first and queues one SQLiteWriteBatch.
- Listing cancellation now deletes the active listing in memory, creates/merges the seller item claim in memory, appends admin memory, then queues one SQLiteWriteBatch.
- Listing expiration now follows the same queued batch pattern per expired listing.
- Item-claim delivery now reduces/deletes the claim in memory, appends admin memory, performs the synchronous inventory insert, then queues one SQLiteWriteBatch.
- Money-claim payout now clears the in-memory balance, performs the synchronous Vault payout, appends admin memory, then queues one SQLiteWriteBatch.
- Relist-from-claim now creates/merges the listing in memory, reduces the claim in memory, appends admin memory, then queues one SQLiteWriteBatch.

Notes:

- Bukkit/Paper inventory work and Vault calls still happen synchronously.
- SQLite durability is deferred through SQLiteWriteBehindQueue.
- Rollback helpers now restore memory only for these pre-enqueue flows.
- ListingWriteHelper now exposes createOrMergeInMemory(), rollbackWriteInMemory(), and putMutation() so services can build combined write batches without direct SQL writes.

Remaining direct-write / maintenance work:

- Purge or restrict old direct append/save/delete APIs that survived the write-queue conversion. They should not remain easy to call from future services/menus.
- Move any remaining price recalculation, runtime-state, custom/admin config, and market index writes behind the queue unless they are explicitly admin-maintenance operations.
- Add a storage maintenance gate so purge/delete/checkpoint/VACUUM/reload paths cannot run at the same time as normal queued writes.
- Make reload command scopes explicit and async: config reload, menu reload, price reload/recalc, and storage diagnostics should not be one blocking full-runtime reload.
- Keep reload and maintenance WAL-aware by using the same configured SQLite connection/worker path.
- Replace size-pressure cleanup with time-based retention.
- Merge sale history/admin history into canonical market_events.
