# DivineMarketplace Project Structure TODO

This file tracks architecture cleanup, package organization, source readability, and larger refactor goals. Feature-specific behavior should stay in `plan.txt` unless it directly affects package structure.

## Current platform baseline

- Target: Minecraft/Paper 1.21.11 with Paper API 26.1.2.
- Runtime: Java 25.
- Storage: SQLite by default through bundled SQLite JDBC and HikariCP.
- Paper and Vault remain server-provided dependencies.
- SQL-backed runtime data currently includes listings, item claims, money claims, market events (canonical history), market indexes, recommendation state, price history, runtime state, custom enchant metadata, and custom item overrides.
- Text config still matters for editable bootstrap definitions: config.yml, category_config.yml, categories.csv, custom/custom_items.yml, custom/custom_enchants.yml, and menu.yml.

## Current high-priority architecture target

Before more file moving, the codebase needs an async-safe runtime and a cached GUI layer:

- Minecraft/Paper API access stays sync/main-thread: chest GUI operations, inventory reads/writes, item delivery, messages, player/world/entity access, and likely Vault/economy calls.
- Plain plugin data work should move async where safe: SQL startup reads, SQL writes, page/search preparation from snapshots, analytics, price recalculation, and reports.
- Runtime memory should be the source of truth after startup. Normal GUI/command reads should use memory indexes, not SQL.
- SQLite writes should flow through one controlled write-behind queue and transaction batches.
- Old direct append/save/delete methods should be purged, hidden, or marked startup/admin-only once queued replacements exist.
- Immutable snapshots should feed async work; async work returns deltas that the runtime validates/applies.
- Player/object action locks should guard dangerous actions so spam-clicking cannot double-purchase, double-cancel, double-claim, or double-pay.
- GUI rendering needs templates, cached static pieces, cached plain page models, and data-version invalidation. Final Bukkit inventory creation/opening remains sync.
- History should move toward one canonical market event stream shared by player history, admin audit, analytics, and future Discord webhooks. (DONE — market_events implemented)
- Cleanup should move toward time-based retention instead of storage-size-driven deletion. (PARTIAL — market events are time-retained; abandoned item claims remain size+age gated)
- Purge/delete/VACUUM/checkpoint maintenance must be mutually exclusive with the normal write-behind worker.
- Reload should be targeted and async, with command-specific paths such as config/menu/prices instead of one blocking full-runtime SQL reload.

## Refactor goal

Move the plugin code into clear layers:

- `bootstrap` / `runtime`
- `storage`
- `store`
- `registry`
- `service`
- `event` or `market_event`
- `gui`
- `command`
- `prompt`
- `scheduler`
- `util`

The result should be:

- Main plugin class owns Paper lifecycle only.
- Runtime/bootstrap owns dependency wiring.
- Registries own definitions/lookups.
- Services own business rules.
- Stores own memory state, indexes, and persistence queues.
- Commands, GUI screens, chat prompts, and scheduled jobs are adapters over services.
- SQL backend details do not leak into GUI or command handlers.
- Public player views, admin views, analytics, and Discord hooks are projections from shared event/data models wherever practical.

## Package responsibilities

### bootstrap / runtime

Owns plugin lifecycle and object graph construction.

Expected responsibilities:

- enable/disable ordering
- runtime state: STARTING, LOADING_STORAGE, READY, RECOVERING_STORAGE, FAILED, DISABLING
- async startup bootstrap
- module construction
- dependency injection by constructor
- shutdown flush ordering
- scheduler/command/listener registration handoff

Should avoid:

- marketplace rules
- GUI rendering
- SQL row encoding
- command parsing
- direct player-facing business messages

### storage

Owns database connections, schema setup/migrations, backend-specific SQL, write-behind queues, transactions, storage maintenance gates, targeted reload paths, and fail-fast storage recovery.

Expected subareas:

- `storage/sqlite/core`
- `storage/sqlite/market`
- `storage/sqlite/pricing`
- `storage/sqlite/admin`
- `storage/sqlite/custom`
- future `storage/mysql/...`

Should avoid:

- Bukkit `Player`
- GUI classes
- MiniMessage-facing result wording
- marketplace business rules
- unsynchronized direct writes outside the write-behind queue or explicit maintenance gate

### store

Future target for memory-first runtime stores and indexes.

Examples:

- listing store/index
- item claim store/index
- money claim store/index
- market event store/index
- price/recommendation store/index
- custom item/enchant definition store/index

Should own:

- memory maps
- reverse indexes
- dirty tracking
- snapshot creation
- queued persistence mutations

Should avoid:

- direct GUI rendering
- direct command parsing
- direct SQL connection details

Current code still mixes some store responsibilities inside service/storage classes. Move only after async write/read behavior stabilizes.

### registry

Owns relatively stable definitions and lookups.

Examples:

- category registry
- custom item registry
- custom enchant registry
- item identity registry/resolver data
- permission registry
- menu definition registry
- market key metadata registry

Should answer definition questions, not perform marketplace actions.

### service

Owns behavior and rules.

Examples:

- listing service
- purchase service
- claim service
- pricing/recommendation service
- history service
- storage maintenance service
- admin review service
- market event publication service
- snapshot/page preparation service

Services may use registries and stores. Services should not know SQL implementation details.

### event / market_event

Target layer for canonical market history.

Owns:

- event records
- event types
- event projections
- player history views
- admin audit views
- webhook/event subscribers
- analytics views

This has replaced duplicated player sale history and admin history writes. Player sale history and admin audit views are now projections from market_events.

### gui

Owns chest inventory framework and screens.

Target framework pieces:

- menu template
- static slot template
- content region
- filler/border slots
- button model
- disabled button model
- paged content model
- cached page model
- state stack/back navigation
- session version/nonce
- click action routing
- chat prompt handoff
- per-player cleanup

Rules:

- Bukkit `Inventory`, `Player`, and final `ItemStack` rendering/opening stay on the main thread.
- Expensive plain-data preparation should use snapshots and async workers.
- Do not make every screen freehand-build all slots from scratch.

See `GUI_PERFORMANCE_PLAN.md`.

### command

Owns command entry points only.

Should:

- parse command arguments
- check permissions
- call services
- open GUI screens
- start chat prompts
- print service results

Should avoid:

- SQL
- raw store mutation
- marketplace business rules
- large rendering logic

### prompt

Owns short-lived chat input states.

Examples:

- search prompt
- create-listing quantity/price prompt
- relist claim prompt
- admin define custom item/enchant prompt

Should coordinate with services and GUI, not own business rules.

### scheduler

Owns named recurring/one-shot tasks.

Examples:

- write-behind flush
- listing expiration
- price recalculation
- storage recovery retry/alert task if needed
- time-based cleanup
- webhook flush
- async cache warmup

Scheduled jobs should check runtime state before doing work.

### util

Shared helpers only.

Examples:

- money formatting
- date/time formatting
- MiniMessage helpers
- item display formatting
- pagination helpers
- permission helpers
- scheduler bridge helpers
- config validation helpers
- collection/index helpers

Avoid dumping large business classes here.

## Current source organization notes

The current source tree has already moved toward:

- `bootstrap`
- `command`
- `scheduler`
- `auction/service/...`
- `auction/registry/custom`
- `auction/storage/sqlite`

This is better than the old service junk drawer, but not final.

Known remaining organization debt:

- Some classes named `Store` are still SQL-backed stores rather than memory-first stores.
- Some service classes still perform persistence directly.
- Some GUI classes still combine page calculation, inventory rendering, and click behavior.
- Item-claim cleanup is still size+age gated rather than purely time-based (intentional for now — claims are active player state).

## Interface / enum guidance

Do not reintroduce large grouped files like:

- `MarketEnums.java`
- `ServiceContracts.java`
- `OperationResults.java`

Those caused Java compile problems and made ownership harder to read.

Preferred rules:

- One public type per Java file.
- Put small interfaces/enums beside their implementation or owning feature folder.
- Only merge an interface/enum into a class when it is private/package-private and only used by that class.
- Public service contracts should stay separate files.
- Result records should stay separate files if multiple classes use them.
- Nested enums are okay when the enum is tightly owned by one result/model.

## GUI performance TODO

Highest-priority GUI work:

1. Add a template/model layer before adding more marketplace screens.
2. Cache static visual specs: filler panes, border icons, back/next/previous icons, disabled buttons, and static labels.
3. Add cached plain page models keyed by screen/filter/page/player-context/data-version.
4. Add data-version counters for listings, claims, prices, categories, menu config, and market events.
5. Prepare expensive page/search/history models async from immutable snapshots.
6. Render final Bukkit inventories sync from prepared page models.
7. Convert listing browser or search results first.
8. Then convert sale history, price history, category/group browsing, and claims.
9. Add session version/nonce checks so stale clicks from old inventories are ignored.
10. Measure before/after with simple debug timings before adding more complexity.

Do not cache live Bukkit inventories globally unless a future library/framework proves that it is safe and worth it. Prefer caching plain templates/page models, then render per player on demand.

## Async/storage TODO

1. Finish write-queue compliance for lower-risk/background writes.
2. [x] Purge/rename direct storage methods — purgeEventsOlderThan → maintenancePurgeEventsOlderThan, purgeOldestAbandonedClaims → maintenancePurgeOldestAbandonedClaims, replaceAll → adminReplaceAll (market index). Dead SQLiteMarketPriceStore.replaceAll removed.
3. [x] Storage maintenance gate exists (runExclusiveMaintenance). Purge/VACUUM/checkpoint are mutually exclusive with the write-behind worker.
4. Convert price recalculation to snapshot -> delta -> memory commit -> queued SQL batch.
5. Convert runtime-state writes to queued SQL batch.
6. Decide final handling for admin/custom metadata writes. Prefer queued writes unless a command must block for immediate admin feedback.
7. [x] Storage recovery implemented: failed flush → RECOVERING_STORAGE, reconnect, retry, FAILED after bounded retries.
8. [x] Redundant shutdown double-flush removed (listingStore.flushPendingWritesBlocking in disable()).
9. Make `/market reload` targeted and async. Split reload into specific scopes such as config/menu/prices; do not synchronously reload SQL-backed runtime data on the main thread.
10. Ensure reload paths use SQLite WAL and coordinate through the same storage worker/maintenance gate instead of opening an unsynchronized side channel.
11. Add snapshot APIs for listing, claim, price, category, event, and menu state.
12. Use snapshots for async search/page/history/price preparation.
13. Keep purchase/cancel/claim/relist final authority on locked current memory.

## Market event merge — DONE

All items below are implemented. Old duplicate stores (SQLiteSalesStore, SQLiteAdminHistoryStore, InMemorySaleHistoryIndex) have been deleted.

1. [x] Added `MarketEventRecord`, `MarketEventType`, `SQLiteMarketEventStore`.
2. [x] Added `MarketEventService` interface + `DefaultMarketEventService` with in-memory indexes.
3. [x] Routed sale completion, listing, cancel, expire, claim, relist into the event stream.
4. [x] Player history reads BUY event projections (SaleRecord views).
5. [x] Admin history reads all event projections (AdminTransactionRecord views).
6. [x] Old duplicate stores deleted.

## Time-based cleanup

1. [x] Market events: time-based retention via marketEventRetentionDays (default 90, 0 = keep forever).
2. [x] Active listings protected — separate table, not touched by event retention.
3. [x] Item claims: size+age gated (itemClaimsSoftMaxMb + abandonedItemClaimDays); unclaimed active claims are never purged.
4. [ ] Price checkpoints: no retention policy yet.
5. [x] Cleanup runs on startup and via /market storage cleanup only — no scheduled row deletion.
6. [x] Cleanup is mutually exclusive with the write-behind queue (runExclusiveMaintenance gate).
7. [x] VACUUM/checkpoint only runs through the maintenance gate.

## Comment/documentation TODO

- [x] All 25 packages have `package-info.java` files.
- [x] Non-trivial files have Layer/Owns/Calls/Avoids headers.
- [ ] Remove any remaining vague source TODOs.
- Keep long-term plans in root markdown/text docs, not placeholder Java classes.

## Quarantine policy

Current quarantine files are reference-only.

Rules:

- Do not restore quarantined Java files directly.
- Many quarantine files are stale or have wrong public-type/file-name pairing from the broken refactor.
- Use quarantine only to recover design ideas after checking against the current compiled source.
- Keep quarantine until async startup, write queue compliance, market event merge, and time-based cleanup are stable.
- After that, archive/delete quarantine.

## Immediate recommended patch order

1. Finish write-queue compliance for background/runtime writes.
2. Add snapshot/version APIs.
3. Add GUI template/cache skeleton.
4. Convert listing browser or search results to cached page models.
5. Convert price recalculation to snapshot/delta/queued write.
6. [x] Add canonical market events.
7. [x] Move admin/player history to event projections.
8. [x] Replace size-based event cleanup with time-based retention.
9. Add package-level docs and final source comments.