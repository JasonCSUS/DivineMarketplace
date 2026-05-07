package divinejason.divinemarketplace.menu;


/*
 * File role: Stores a validated Material/custom-model-data pair for configurable GUI icons.
 */
import java.util.List;
import org.bukkit.Material;

/**
 * Editable visual definition for one GUI item.
 *
 * Loaded from menu.yml so server owners can swap materials, names, lore, and custom-model icons
 * without touching Java code.
 */
public record MenuIconSpec(
        Material material,
        String name,
        List<String> lore,
        Integer customModelData
) {
    public static MenuIconSpec of(Material material, String name, List<String> lore, Integer customModelData) {
        return new MenuIconSpec(material == null ? Material.BARRIER : material, name == null ? "" : name, lore == null ? List.of() : List.copyOf(lore), customModelData);
    }
}
