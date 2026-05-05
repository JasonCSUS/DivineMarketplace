package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable custom item type extraction result data between marketplace services, persistence stores, commands, and GUI rendering.
 */
import org.bukkit.Material;

import java.util.List;

public record CustomItemTypeExtractionResult(
        boolean custom,
        boolean treatAsVanilla,
        boolean provisional,
        String itemType,
        Material requiredMaterial,
        Float requiredCustomModelData,
        String matchedRuleId,
        String matchedValue,
        String signature,
        List<String> ruleTrace
) {
    public boolean hasCustomSignal() {
        return custom && !treatAsVanilla && itemType != null && !itemType.isBlank();
    }
}
