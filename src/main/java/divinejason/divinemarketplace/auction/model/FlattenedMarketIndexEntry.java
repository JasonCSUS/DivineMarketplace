package divinejason.divinemarketplace.auction.model;

import org.bukkit.Material;

public record FlattenedMarketIndexEntry(
        String recordType,
        String marketKey,
        String marketDisplayName,
        String categoryId,
        String itemType,
        Material requiredMaterial,
        Float requiredCustomModelData,
        CustomItemDefinitionState definitionState
) {
    public FlattenedMarketIndexEntry {
        if (definitionState == null) {
            definitionState = CustomItemDefinitionState.CONFIRMED;
        }
    }
    public boolean isCustom() {
        return "CUSTOM".equalsIgnoreCase(recordType);
    }

    public boolean isVanilla() {
        return "VANILLA".equalsIgnoreCase(recordType);
    }
}
