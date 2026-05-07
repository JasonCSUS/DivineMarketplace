package divinejason.divinemarketplace.auction.service.identity;

/*
 * Layer : service
 * Owns  : listing policy resolver behavior
 * Calls : stores (auction/storage) and registries only — never GUI or commands
 */


/*
 * File role: Resolves listing policy decisions used by listing, browsing, and marketplace policy code.
 */
import divinejason.divinemarketplace.config.ConfigService;
import org.bukkit.entity.Player;

/**
 * Shared helper for resolving effective listing limits/durations from config and permissions.
 */
public final class ListingPolicyResolver {

    public ListingPolicy resolve(Player seller) {
        int maxListings = ConfigService.get().listingDefaultMaxActiveListings();
        int listingDurationDays = Math.max(1, (int) (ConfigService.get().listingDefaultDurationMillis() / 86_400_000L));

        for (var tier : ConfigService.get().getMainConfig().listingPolicies().tiers()) {
            if (tier.permission() == null || tier.permission().isBlank()) {
                continue;
            }
            if (!seller.hasPermission(tier.permission())) {
                continue;
            }

            if (tier.maxListings() > maxListings) {
                maxListings = tier.maxListings();
            }
            if (tier.listingDurationDays() > listingDurationDays) {
                listingDurationDays = tier.listingDurationDays();
            }
        }

        return new ListingPolicy(maxListings, listingDurationDays);
    }

    public record ListingPolicy(int maxListings, int listingDurationDays) {
    }
}
