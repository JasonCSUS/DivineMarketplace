package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.SubcategorySummary;

import java.util.List;
import java.util.Optional;

/**
 * Search should resolve players into market groups first, not raw listings.
 */
public interface SearchService {

    /**
     * Fuzzy-search market groups by market display name, market key, and any
     * configured searchable aliases.
     *
     * PSEUDOCODE:
     * - normalize query
     * - prefer exact category/subcommand matches before fuzzy search
     * - case-insensitive partial matching is enough for v1
     * - return market-group summaries, not raw listings
     */
    List<SubcategorySummary> searchMarketGroups(String query, int page, int pageSize);

    /**
     * Resolve a command token directly to an exact market group if possible.
     */
    Optional<SubcategorySummary> resolveExactMarketGroup(String token);
}
