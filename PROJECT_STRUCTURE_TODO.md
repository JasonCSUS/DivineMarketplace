# DivineMarketplace TODO Map

This file is the root TODO/status map. Source-side TODO map files should stay empty or be removed so this remains the planning source of truth.

## Current truth

- Target: Minecraft/Paper 1.21.11 with Paper API 26.1.2.
- Runtime state is SQLite-backed in `data/market.db`.
- Item claims are not split into separate owner-hash files. Claim persistence is SQLite.
- Per-player GUI/menu/chat state is split by player UUID in memory so multiple players can use the market at the same time without sharing sessions or prompt state.
- Custom item auto-discovery/admin definition writes to the SQLite-backed market index. Unknown/flagged item metadata should also be written to readable log files for server-owner review.
- `/market` is the primary GUI entry. Text commands remain as direct/fallback paths.
- Listing, relisting, and GUI search use chat prompts.
- `/market pricecheck` and `/market pc` provide the quick held-item recommendation lookup players need before listing.
- Category display names/icons/order are loaded from `category_config.yml`; player-facing output should prefer display names over raw ids.
- SQLite storage maintenance tracks aggregate database/WAL/SHM size, enforces logical per-table history limits, purges abandoned item claims only when the `item_claims` table exceeds its soft limit, and compacts SQLite after cleanup.

## Locked player loop for alpha

```text
/market
  -> browse/search/list/pricecheck
  -> inspect listing
  -> buy/cancel
  -> claim/relist
  -> history/price history
```

## Done/current

- Java source comments now describe current file roles, and old planning comments were rewritten to current behavior.
- YAML defaults include short header comments for server-owner editable files.

- SQLite stores exist for listings, item claims, money claims, sales, admin history, market index, current prices, price history, runtime state, custom enchants, and custom item overrides.
- Item purchases route to buyer item claims, not direct inventory insertion.
- Seller proceeds route to money claims and pay out through Vault.
- Listing creation and relisting return structured results.
- Claim item and claim money actions now return structured result objects for exact GUI/command feedback.
- `/market` opens the GUI.
- `/market claim` opens claims GUI.
- GUI item detail has owner, buyer, and admin modes.
- GUI search/list/relist use short-lived per-player chat prompts.
- GUI pagination loads one extra result and renders Next only when a next page exists.
- `menu.yml` loader validates editable slots/materials/custom model data and falls back safely.
- Sale/price history GUI timestamps render as simple server-local date/time strings.
- `/market pricehistory <display name> menu` opens the price-history GUI with month navigation over months that contain data.
- Enchanted-book sale history uses component-aware matching.
- Mixed enchanted books stay visible in sale history but remain excluded from price training.
- `/market storage` and `/market storage cleanup` exist for admin storage inspection/manual cleanup.

## Files intentionally no longer used for planning

- `src/main/java/divinejason/divinemarketplace/design/ProjectTodoMap.java` is absent from the current source tree. Keep planning in the root docs instead of reintroducing source-side TODO maps.

## Deletion review

No stale service/repository wrappers were present in the latest zip for these previously marked files:

- `auction/service/GenericCustomItemTypeExtractor.java`
- `auction/service/NoopCustomItemTypeExtractor.java`
- `auction/repository/ClaimRepository.java`
- `auction/repository/ListingRepository.java`
- `auction/repository/SaleRepository.java`

Keep these reviewed as absent unless they reappear in a later merge.

## Remaining alpha-hardening checks

- Scan normal player messages for accidental market-key/internal-id exposure.
- Review admin command UX and decide which flows are command-only for alpha versus worth adding to GUI review screens.
- Confirm all GUI buttons have either a real action or intentionally disabled visual state.
- Verify `plugin.yml` permissions against generated/readable `permissions.txt`, including storage-admin permission.
- Compile has been reported successful by the project owner; verify runtime behavior with Paper startup logs and hands-on multiplayer testing.
