package divinejason.divinemarketplace.command;

/*
 * Layer : command wiring
 * Owns  : construction of the /market BasicCommand dependency graph
 * Calls : MarketRuntime getters only
 * Avoids: business logic, storage mutation, and command execution behavior
 *
 * DivineMarketplace.java should not know every command dependency.  When a new
 * command handler needs another service, wire it here so the plugin entry point
 * remains a Paper lifecycle class instead of a command composition class.
 */

import divinejason.divinemarketplace.DivineMarketplace;
import divinejason.divinemarketplace.bootstrap.MarketRuntime;

public final class MarketCommandFactory {

    public MarketCommand create(DivineMarketplace plugin, MarketRuntime runtime) {
        return new MarketCommand(
                plugin,
                runtime.getMenuController(),
                runtime.getChatPromptService(),
                runtime.getListingService(),
                runtime.getClaimService(),
                runtime.getListingStore(),
                runtime.getHistoryService(),
                runtime.getAdminHistoryService(),
                runtime.getAdminHistoryExportService(),
                runtime.getCategoryService(),
                runtime.getFlattenedMarketIndexService(),
                runtime.getPriceRecommendationService(),
                runtime.getItemIdentityResolver(),
                runtime.getCustomItemRegistry(),
                runtime.getMarketRecalculationService(),
                runtime.getEnchantmentMetadataService(),
                runtime.getCustomItemTypeExtractor(),
                runtime.getCustomItemMetadataLogService(),
                runtime.getCustomItemOverrideStore(),
                runtime.getCustomItemCollisionLogService(),
                runtime.getStoredEnchantExtractor(),
                runtime::isReady
        );
    }
}
