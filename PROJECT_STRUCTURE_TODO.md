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
    listings.bin
    item_claims.bin
    money_claims.bin
    sales.bin
    market_profiles.bin
    package_cache.bin
    unknown_custom_items.bin
    unknown_custom_enchants.bin
    admin_sales.bin
    admin_listings.bin
    admin_claims.bin
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
- final `MarketProfile` field set
- exact recommendation-history / compact price-history model if included in v1
- exact command outputs for market profile / history views

### Market calculator + scheduler
- `MarketProfileCalculator` is mostly pure Java and should stay minimally dependent on Paper/Bukkit
- use averages + simple regression slope/fitness + clamped rule-based movement
- avoid calculus
- `MarketRecalculationService` should:
  - compare last global recalc time against current time
  - queue market keys that are eligible
  - skip items recalculated too recently
  - process only a small number of items per run/tick
  - support manual/emergency single-item recalculation
- manual item recalculation should update `lastRecalculatedAtEpochMillis` so the next global/daily pass skips that item until the per-item interval has elapsed

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
- exact singular/mixed enchant Sale History query implementation details inside `BinarySalesStore` / `HistoryService`
- exact Paper inventory API details for safe item removal/delivery while preserving the locked behavior
- exact registration/bootstrap wiring in `DivineMarketplace.java`
- old duplicate UI package under `auction/ui/**` should be removed so the new `menu/**` package is the only menu system
