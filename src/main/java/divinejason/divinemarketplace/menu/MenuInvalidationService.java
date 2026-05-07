package divinejason.divinemarketplace.menu;

/*
 * Layer : gui/cache
 * Owns  : Centralized invalidation for cached menu page models.
 * Calls : MenuDataVersion and MenuPagePreparationService.
 * Avoids: GUI rendering, SQL, and marketplace business logic.
 */

import java.util.Objects;

/**
 * Single gateway for marking cached menu data stale.
 *
 * <p>Marketplace services, click handlers, prompts, reload flows, and scheduled
 * jobs should call this instead of directly poking {@link MenuDataVersion}. That
 * keeps cache-invalidation rules readable as the GUI cache grows.</p>
 */
public final class MenuInvalidationService {
    private final MenuDataVersion dataVersion;
    private final MenuPagePreparationService pagePreparationService;

    public MenuInvalidationService(MenuDataVersion dataVersion, MenuPagePreparationService pagePreparationService) {
        this.dataVersion = Objects.requireNonNull(dataVersion, "dataVersion");
        this.pagePreparationService = Objects.requireNonNull(pagePreparationService, "pagePreparationService");
    }

    public MenuDataVersion dataVersion() {
        return dataVersion;
    }

    public void markListingsChanged() {
        dataVersion.markListingsChanged();
    }

    public void markClaimsChanged() {
        dataVersion.markClaimsChanged();
    }

    public void markSalesHistoryChanged() {
        dataVersion.markSalesHistoryChanged();
    }

    public void markCategoriesChanged() {
        dataVersion.markCategoriesChanged();
    }

    public void markPricesChanged() {
        dataVersion.markPricesChanged();
    }

    public void markListingPurchaseCompleted() {
        dataVersion.markListingsChanged();
        dataVersion.markClaimsChanged();
        dataVersion.markSalesHistoryChanged();
        dataVersion.markPricesChanged();
    }

    public void markListingInventoryChanged() {
        dataVersion.markListingsChanged();
    }

    public void markListingAndClaimsChanged() {
        dataVersion.markListingsChanged();
        dataVersion.markClaimsChanged();
    }

    public void markClaimStateChanged() {
        dataVersion.markClaimsChanged();
    }

    public void markMenuConfigChanged() {
        dataVersion.markMenuConfigChanged();
        pagePreparationService.invalidateAll();
    }

    public void markAllChanged() {
        dataVersion.markListingsChanged();
        dataVersion.markClaimsChanged();
        dataVersion.markSalesHistoryChanged();
        dataVersion.markCategoriesChanged();
        dataVersion.markPricesChanged();
        dataVersion.markMenuConfigChanged();
        pagePreparationService.invalidateAll();
    }
}
