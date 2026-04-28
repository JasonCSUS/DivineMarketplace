# DivineMarketplace TODO Map

This file tracks what is conceptually locked vs what still needs a decision.
It should stay short and high-signal.

Detailed behavior notes belong inside the relevant Java files and example configs.
`plan.txt` should describe what each subsystem does and how it works.

## Locked for v1

### Core folder layout

```text
plugins/DivineMarketplace/
  config.yml
  category_config.yml
  permissions.txt

  categories/
    <category>.yml

  custom/
    custom_items.yml
    custom_enchants.yml

  logs/
    admin_transactions.log
    unknown_custom_items.log
    unknown_custom_enchants.log
    exports/

  data/
    market.db
    package_cache.bin
    unknown_custom_items.bin
    unknown_custom_enchants.bin
```

### Main config access
- `config.yml` should be parsed by a dedicated `MainConfigLoader`
- the parsed result should live in `MainConfig`
- `ConfigService` should act as a singleton access point to the current MainConfig
- callers should prefer specific helper getters for individual config values
- old `PluginConfig.java` is stale and should be deleted/replaced by:
  - `MainConfig.java`
  - `MainConfigLoader.java`
  - `ConfigService.java`

### Money representation
- internal money storage should use integer hundredths
- no float/double money math should be used internally
- convert to decimal only when:
  - displaying values to players
  - calling Vault APIs

### Binary storage / cleanup
- `listings.bin` stores active listings only and is not size-purged
- `item_claims.bin` stores live owed item claims and only purges abandoned claims when over soft limit
- `money_claims.bin` stores one pending balance per player and deletes zero balances
- `sales.bin` stores recent exact market sales and may be FIFO-purged by size
- `admin_sales.bin`, `admin_listings.bin`, and `admin_claims.bin` are separate FIFO-purged audit files

### Custom item discovery / definition
- custom items should be matchable by stable identity when available
- if stable identity is not available, fallback matching may use:
  - requiredMaterial
  - requiredCustomModelData
- newly discovered custom items may be auto-defined immediately
- new auto-defined custom items should default to categoryId = `unsorted`
- newly discovered custom items should always get admin review
- safe new items = `ReviewFlagLevel.NORMAL`
- ambiguous/system-risk items = `ReviewFlagLevel.HIGH_PRIORITY`
- all items may still be listed
- unresolved unsafe items may become `BrowseVisibility.RECENT_ONLY`

### Category / subcategory behavior
- top-level categories are config-defined and always shown
- top-level categories show live listing counts in lore, including 0
- subcategories are generated dynamically from active listings only
- empty subcategories are not shown
- `marketDisplayName` is the grouped/subcategory label shown to players
- category icon config truth is:
  - `"MATERIAL"`
  - `"MATERIAL:FLOAT_MODEL_DATA"`
- blank generated category files should stop at:
  - `items:`

### Admin auditing
- all market actions must be written to binary admin history
- admin transaction history is mandatory and not item-dependent
- player-facing market history is separate from admin history
- market training data is separate from admin history
- admins should be able to:
  - query history in game
  - export readable text reports from binary history

### Listing behavior
- listings.bin stores active listings only
- v1 listing source is one held item / one explicit source stack only
- server must re-read the real server-side item at confirm time
- server must remove and verify the item before creating or merging a listing
- `Listing` stores:
  - listingId
  - sellerUuid
  - listedItemSnapshot
  - amount
  - marketKey
  - marketDisplayName
  - unitPrice
  - listedAtEpochMillis
  - listingDurationMillis
- total price is derived: `unitPrice * amount`
- expiration is derived: `listedAtEpochMillis + listingDurationMillis`
- mergeable listings refresh `listedAtEpochMillis` to the newest listing time
- v1 has no mass listing

### Claim behavior
- item claims and money claims are separate models and separate binary files
- `ItemClaimRecord` stores:
  - claimId
  - ownerUuid
  - claimItemSnapshot
  - amount
  - createdAtEpochMillis
- similar item claims for the same owner may merge and refresh `createdAtEpochMillis`
- left click claims one safe stack-sized chunk
- shift-click claims as much as safely fits in inventory
- claim is removed when amount reaches zero
- abandonment is derived from `createdAtEpochMillis`
- `MoneyClaimRecord` stores:
  - ownerUuid
  - amount
- sale occurs -> add to player's earnings balance
- player claims earnings -> balance resets to 0 and the record is deleted
- missing money claim record means zero balance

### Sale history / training behavior
- `SaleRecord` is player-facing exact sale history and also the source dataset for training
- `SaleRecord` stores:
  - marketKey
  - marketDisplayName
  - soldItemSnapshot
  - amountPurchased
  - unitPrice
  - soldAtEpochMillis
  - marketTrainingParticipation
- total sale price is derived: `unitPrice * amountPurchased`
- every SaleRecord is player-facing market history by definition
- training is a subset of player-facing sale history
- mixed books remain in history but are excluded from training

## Still needs discussion
- exact command outputs for market profile / history views

### Market calculator + scheduler
- `MarketProfileCalculator` is mostly pure Java and should stay minimally dependent on Paper/Bukkit
- it may still return a `MarketProfile` object as an in-memory calculation result only
- use averages + simple regression slope/fitness + clamped rule-based movement
- avoid calculus
- `MarketRecalculationService` should:
  - compare the current day against a tiny global last-recalc-day state value
  - queue all eligible market keys for the daily pass
  - process only a small number of items per run/tick
  - support manual/emergency single-item recalculation
- final recommended prices should be written to `market_prices.csv`
- no live plugin wiring should depend on `market_profiles.bin`

### Menu system outline
- chest menus are now storyboarded enough to scaffold clean menu architecture
- locked v1 menu views:
  - `MAIN`
  - `CATEGORY_BROWSER`
  - `SEARCH_RESULTS`
  - `LISTING_BROWSER`
  - `ITEM_DETAIL`
  - `MY_LISTINGS`
  - `CLAIMS`
  - `SALE_HISTORY`
  - `PRICE_HISTORY`
- search should be command-driven:
  - `/market search <query>`
- the Search button should instruct the player to use the command rather than intercepting chat in v1
- menu rules:
  - menus are views, not source-of-truth
  - listener stays thin
  - renderer stays dumb
  - services do business logic
  - session stores UI state only
- remaining storyboard TODOs are small:
  - confirm exact filler materials/colors during Paper implementation
  - confirm whether disabled history buttons on All Listings are hidden or shown as locked filler
  - confirm final slot constants if any last-minute visual tuning happens while applying Paper API

### Full pseudocode pass status
- core persistence stores now have read/write/update/delete pseudocode scaffolding
- core services now have create/buy/cancel/claim/history pseudocode scaffolding
- menu architecture is scaffolded enough to begin Paper implementation one file at a time
- command scaffolds are aligned to the locked v1 flow

### Pseudocode intentionally deferred / still slightly fuzzy
- exact singular/mixed enchant Sale History query implementation details inside `SQLiteSalesStore` / `HistoryService`
- exact Paper inventory API details for safe item removal/delivery while preserving the locked behavior
- exact registration/bootstrap wiring in `DivineMarketplace.java`
- old duplicate UI package under `auction/ui/**` should be removed so the new `menu/**` package is the only menu system

### Config bootstrap / loader layer
- bundled resource files under src/main/resources are shipped default templates, not live editable files
- live editable files are copied into plugins/DivineMarketplace/ on first run
- locked shipped exact defaults:
  - config.yml
  - category_config.yml
  - custom/custom_items.yml
  - custom/custom_enchants.yml
  - permissions.txt
- default category_config.yml now starts with vanilla top-level categories only
- categories/<category>.yml files are generated as live files under the plugin folder
- MainConfig/MainConfigLoader/ConfigService are now the startup config parse/cache chain
- remaining TODO:
  - generate exact populated vanilla category item files later once category taxonomy + denylist generation pass is locked

### Exact bundled category defaults
- built-in category files in `src/main/resources/defaults/categories/` are now treated as exact shipped defaults, not examples
- `PluginFileInitializer` should copy exact bundled category defaults for built-in categories
- blank category files should only be generated for custom categories added later
- on first bundled-default copy, category coverage validation should warn about:
  - invalid material names
  - duplicate materials across built-in category files
  - uncategorized allowed vanilla materials

### Default shipped categories
- shipped vanilla top-level defaults now include `ores`
- `ores` should hold ore blocks, raw ore items/blocks, processed ingots/nuggets/gems, and storage blocks for the major ore families
- matching items should be removed from broad buckets like `materials` and `redstone`

### Startup wiring
- startup should now be implemented for:
  - live folder/file bootstrap
  - config load
  - config cache
- `DivineMarketplace` should disable itself cleanly if bootstrap or config load fails
- deeper repository/service/menu wiring remains for later milestones

### Binary store implementation notes
- binary stores are now safe to implement mostly as plain Java file handlers
- ItemStack snapshots should use Paper byte serialization helpers, not Bukkit object streams
- current low-risk implementation direction is read-full-file / mutate / rewrite atomically
- known remaining hiccups:
  - mixed/singular enchant Sale History expansion should stay above the low-level sales store
  - recommendation history still does not have a dedicated binary store/file in the current layout
  - if performance later becomes a problem, append-only formats can replace the simpler rewrite model

### Item claims storage refinement
- item claims should no longer be treated as one giant `item_claims.bin`
- v1 storage helper direction is:
  - owner-hash sharded claim files
  - shard metadata sidecars
  - cleanup only scans candidate shards
  - cleanup targets enough purge to get below `max - 10% of max`
  - candidate shards are prioritized by oldest `lastActivityAtEpochMillis`
- important: storage helpers do not schedule cleanup on their own

### ListingService main-hand-only refinement
- normal listing flow should be main-hand only
- listing should not scan the rest of the inventory for a similar stack
- requested quantity should clamp down to the current main-hand amount
- multi-stack or broader inventory listing should only happen under an explicit later command/flow

### Listing structural correction
- `Listing` should store `categoryId` directly
- normal browse/index flows should not need to re-resolve item identity to recover a listing category
- listing creation should return a result object with:
  - success/failure
  - failure reason
  - debug message
  - actual clamped quantity
- lightweight admin history status/reason strings are acceptable for now

### Claim-to-listing relist flow
- players should be able to relist directly from claims
- main reason: cancel a listing, then relist at a different price without moving the item back through inventory first
- relist should:
  - be owner-only in normal flow
  - support partial relist quantity
  - clamp requested quantity to the claim amount
  - create a fresh listing from claimItemSnapshot
  - decrement or delete the claim only after listing success
  - return a structured ListingCreateResult for clean player/debug feedback
- claim-based relist should live under ClaimService rather than the normal main-hand ListingService path

### Custom item auto-definition persistence
- auto-discovered custom items should be written through to `custom/custom_items.yml`
- persisted auto-definitions should include:
  - itemType
  - requiredMaterial
  - requiredCustomModelData
  - marketDisplayName
  - categoryId = unsorted
- a registry/data-source helper should own the write-through path so later admin sort/define commands can reuse it
- only filled bundles/shulkers should take the package/container path
- mixed enchanted books only need stable identity + correct history/training flags for now; richer browse/history behavior can be refined later
\n
### ClaimService implementation direction
- ClaimService should now be able to:
  - redeem one safe stack-sized chunk
  - redeem as much as safely fits in storage contents
  - relist directly from claims without routing through inventory first
  - claim earnings through Vault
- relist-from-claim currently duplicates some listing creation logic because
  the normal ListingService flow is intentionally main-hand only
- later cleanup/refactor could move shared listing write logic into a common helper
\n
### Command layer finalization
- `/market` command should use Paper's BasicCommand path for:
  - permission-aware visibility
  - suggestion support
  - rich sender messaging
- player suggestions should prefer market display names
- admin suggestions should expose both market keys and market display names
- low-risk live command paths now include:
  - listing creation
  - claim earnings
  - text-based sale history lookup
- menu-heavy or unfinished maintenance commands may remain explicit placeholders until their backing services are finished


### Legacy profile persistence decoupling
- `BinaryMarketProfileStore.java` is decoupled from live plugin wiring and marked safe for deletion
- `MarketProfileRepository.java` is decoupled from live plugin wiring and marked safe for deletion
- `MarketProfile.java` remains intentionally alive as an in-memory calculation object


### Price recommendation + browse slice
- `DefaultPriceRecommendationService` now owns market_prices.csv load/save and in-memory price state
- `InMemorySaleHistoryIndex` preloads the sales binary into memory at startup for text history/pricehistory/recalc queries
- `MarketRecalculationService` should schedule async single-item or global recalculation jobs and update the tiny runtime-state day file
- `/market` should now be registered in `DivineMarketplace.onEnable()`
- `DefaultCategoryService` should be a real browse/index layer backed by market_index.csv + active listings, not a no-op
- text command paths should prefer:
  - player display names for player UX
  - market keys + display names for admin maintenance UX

### SQLite runtime storage migration
- core runtime transaction state is moving off ad hoc binary files and into one local SQLite database
- the current migration keeps existing store class names temporarily to avoid a huge service-layer rename
- first migrated live tables:
  - listings
  - item_claims
  - money_claims
  - sales
  - admin_sales
  - admin_listings
  - admin_claims
  - runtime_state
- `BinaryStoreSupport.java` and `BinaryAdminStoreSupport.java` are now deletion candidates once no stale references remain


### Store naming cleanup after SQLite migration
- live database-backed stores should now use explicit SQLite naming:
  - `SQLiteListingStore`
  - `SQLiteItemClaimStore`
  - `SQLiteMoneyClaimStore`
  - `SQLiteSalesStore`
  - `SQLiteAdminSalesStore`
  - `SQLiteAdminListingsStore`
  - `SQLiteAdminClaimsStore`
- `SQLiteBlobCodecSupport` should be renamed to `SQLiteRecordCodecSupport`
- legacy `Binary*Store` file names become deletion candidates once references are updated


### SQLite-backed runtime storage
- live runtime market state should now persist in SQLite tables inside `data/market.db`
- current migrated runtime tables include:
  - market_index
  - market_prices
  - price_history
  - listings
  - item_claims
  - money_claims
  - sales
  - admin_sales
  - admin_listings
  - admin_claims
  - runtime_state
- category/custom definition text files remain the editable source layer
- flattened runtime lookup and current recommendation data should no longer depend on CSV persistence

### Final runtime wiring cleanup
- live runtime category/search/admin wiring should now read from SQLite-backed market index data, not from the old generated CSV/YAML runtime path
- seed YAML/resource files remain bootstrapping inputs only when the SQLite market index is empty
- custom item definitions should now load/write through the SQLite-backed market index table
- admin sort/define commands should mutate the SQLite-backed market index, not category YAML files
- custom item extraction now uses a generic persistent-data-key heuristic instead of the old no-op extractor


- plugin.yml permission nodes for inspect/custom override are now under the permissions block
- inspect raw now obeys config, and history exports require export permission
- provisional unknown snapshot logging is now wired through the item identity resolver


- plugin.yml uses Minecraft api-version 1.21 and keeps inspect/custom override permissions under the permissions block
- provisional unknown custom items are now deduped when metadata snapshots are auto-written
- provisional unknown custom items now stay unsorted/review-heavy and are excluded from player-facing market history/training
- custom registry now guards against itemType/material+CMD collisions instead of silently overwriting
- category/subcategory quantity displays are explicitly labeled as qty in text output
