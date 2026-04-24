package divinejason.divinemarketplace.command;

/**
 * Player command scaffold.
 *
 * Locked v1 entry points:
 * - /market
 * - /market all
 * - /market search <query>
 * - /market <category>
 * - /market <marketKey>
 * - /market claim
 * - /market claim earnings
 * - /market list <quantity> <unitPrice>
 *
 * Command resolution order:
 * 1. built-in subcommands (all, search, claim, list)
 * 2. exact category match
 * 3. exact market-key match
 * 4. fuzzy-search fallback
 *
 * PSEUDOCODE:
 * - /market -> open main menu
 * - /market all -> open listing browser with selectedMarketKey = null and NEWEST_FIRST
 * - /market search <query> -> open search results
 * - /market claim -> open claims
 * - /market claim earnings -> pay pending balance directly
 * - /market list <quantity> <unitPrice> -> delegate to ListingService using held/source item
 */
public final class MarketCommand {
}
