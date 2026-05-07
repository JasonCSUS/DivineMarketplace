package divinejason.divinemarketplace.auction.service.enchant;

/*
 * Layer : service
 * Owns  : enchantment metadata behavior
 * Calls : stores (auction/storage) and registries only — never GUI or commands
 */


/*
 * File role: Implements enchantment metadata service behavior using the SQLite stores, config registries, and item identity services.
 */
import divinejason.divinemarketplace.auction.model.EnchantBrowseGroup;
import divinejason.divinemarketplace.auction.model.EnchantmentDefinition;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteCustomEnchantStore;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBatch;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBehindQueue;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * SQLite-backed enchantment metadata lookup.
 *
 * Admin-defined custom enchants are resolved first. Vanilla enchantments still get
 * stable display metadata so enchanted-book identity can use the same path for
 * Bukkit and plugin-provided enchantments.
 */
public final class DefaultEnchantmentMetadataService implements EnchantmentMetadataService {
    private final SQLiteCustomEnchantStore customEnchantStore;
    private final SQLiteWriteBehindQueue writeBehindQueue;
    private final Map<String, SQLiteCustomEnchantStore.CustomEnchantEntry> customEntriesByKey = new LinkedHashMap<>();

    public DefaultEnchantmentMetadataService(SQLiteCustomEnchantStore customEnchantStore, SQLiteWriteBehindQueue writeBehindQueue) {
        this.customEnchantStore = Objects.requireNonNull(customEnchantStore, "customEnchantStore");
        this.writeBehindQueue = Objects.requireNonNull(writeBehindQueue, "writeBehindQueue");
    }

    /** Loads all custom enchant definitions from SQLite into the in-memory lookup. */
    public synchronized void loadFromStorage() {
        customEntriesByKey.clear();
        customEntriesByKey.putAll(customEnchantStore.loadAll());
    }

    public synchronized void reload() {
        loadFromStorage();
    }

    /** Updates the in-memory entry immediately and enqueues the SQLite write. */
    public synchronized void upsert(String namespacedEnchantKey, String displayName, List<String> itemTokens) {
        String normalizedKey = normalizeKey(namespacedEnchantKey);
        customEntriesByKey.put(normalizedKey, new SQLiteCustomEnchantStore.CustomEnchantEntry(normalizedKey, displayName, itemTokens));
        writeBehindQueue.enqueue(SQLiteWriteBatch.builder("custom enchant " + normalizedKey)
                .add(customEnchantStore.upsertMutation(namespacedEnchantKey, displayName, itemTokens))
                .build());
    }

    @Override
    public synchronized EnchantmentDefinition resolveDefinition(String namespacedEnchantKey) {
        String normalizedKey = normalizeKey(namespacedEnchantKey);
        SQLiteCustomEnchantStore.CustomEnchantEntry customEntry = customEntriesByKey.get(normalizedKey);
        if (customEntry != null) {
            return new EnchantmentDefinition(
                    normalizedKey,
                    customEntry.displayName(),
                    browseGroupsFromTokens(customEntry.itemTokens()),
                    true,
                    true,
                    "custom_enchants"
            );
        }

        boolean vanilla = normalizedKey.startsWith("minecraft:");
        return new EnchantmentDefinition(
                normalizedKey,
                humanizeToken(stripNamespace(normalizedKey)),
                vanilla ? builtinVanillaBrowseGroups(normalizedKey) : Set.of(EnchantBrowseGroup.UNKNOWN),
                vanilla,
                !vanilla,
                vanilla ? "bukkit_vanilla" : "unconfigured_custom_enchant"
        );
    }

    private Set<EnchantBrowseGroup> browseGroupsFromTokens(Iterable<String> itemTokens) {
        EnumSet<EnchantBrowseGroup> groups = EnumSet.noneOf(EnchantBrowseGroup.class);
        for (String itemToken : itemTokens) {
            EnchantBrowseGroup group = browseGroupFromToken(itemToken);
            if (group != null) {
                groups.add(group);
            }
        }
        if (groups.isEmpty()) {
            groups.add(EnchantBrowseGroup.UNKNOWN);
        }
        return Set.copyOf(groups);
    }

    private EnchantBrowseGroup browseGroupFromToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        String token = rawToken.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (token) {
            case "sword", "swords" -> EnchantBrowseGroup.SWORD;
            case "axe", "axes" -> EnchantBrowseGroup.AXE;
            case "bow", "bows" -> EnchantBrowseGroup.BOW;
            case "crossbow", "crossbows" -> EnchantBrowseGroup.CROSSBOW;
            case "trident", "tridents" -> EnchantBrowseGroup.TRIDENT;
            case "armor", "armour" -> EnchantBrowseGroup.ARMOR;
            case "helmet", "helm", "head", "helmets" -> EnchantBrowseGroup.HELMET;
            case "chest", "chestplate", "chestplates" -> EnchantBrowseGroup.CHESTPLATE;
            case "legs", "leggings", "pants" -> EnchantBrowseGroup.LEGGINGS;
            case "boot", "boots" -> EnchantBrowseGroup.BOOTS;
            case "tool", "tools" -> EnchantBrowseGroup.TOOLS;
            case "pickaxe", "pick", "pickaxes" -> EnchantBrowseGroup.PICKAXE;
            case "shovel", "spade", "shovels" -> EnchantBrowseGroup.SHOVEL;
            case "hoe", "hoes" -> EnchantBrowseGroup.HOE;
            case "elytra" -> EnchantBrowseGroup.ELYTRA;
            case "shield", "shields" -> EnchantBrowseGroup.SHIELD;
            case "shear", "shears" -> EnchantBrowseGroup.SHEARS;
            case "universal", "all", "any" -> EnchantBrowseGroup.UNIVERSAL;
            default -> EnchantBrowseGroup.UNKNOWN;
        };
    }

    private Set<EnchantBrowseGroup> builtinVanillaBrowseGroups(String normalizedKey) {
        String key = stripNamespace(normalizedKey);
        return switch (key) {
            case "sharpness", "smite", "bane_of_arthropods", "sweeping_edge", "looting", "knockback", "fire_aspect" -> Set.of(EnchantBrowseGroup.SWORD);
            case "power", "punch", "flame", "infinity" -> Set.of(EnchantBrowseGroup.BOW);
            case "quick_charge", "multishot", "piercing" -> Set.of(EnchantBrowseGroup.CROSSBOW);
            case "impaling", "loyalty", "riptide", "channeling" -> Set.of(EnchantBrowseGroup.TRIDENT);
            case "protection", "fire_protection", "blast_protection", "projectile_protection", "thorns", "binding_curse", "vanishing_curse", "mending", "unbreaking" -> Set.of(EnchantBrowseGroup.ARMOR, EnchantBrowseGroup.UNIVERSAL);
            case "aqua_affinity", "respiration" -> Set.of(EnchantBrowseGroup.HELMET);
            case "depth_strider", "frost_walker", "soul_speed", "feather_falling" -> Set.of(EnchantBrowseGroup.BOOTS);
            case "efficiency", "fortune", "silk_touch" -> Set.of(EnchantBrowseGroup.TOOLS);
            default -> Set.of(EnchantBrowseGroup.UNIVERSAL);
        };
    }

    private String normalizeKey(String namespacedEnchantKey) {
        if (namespacedEnchantKey == null || namespacedEnchantKey.isBlank()) {
            return "unknown:unknown";
        }
        return namespacedEnchantKey.trim().toLowerCase(Locale.ROOT);
    }

    private String stripNamespace(String key) {
        int index = key.indexOf(':');
        return index >= 0 ? key.substring(index + 1) : key;
    }

    private String humanizeToken(String token) {
        String[] parts = token.toLowerCase(Locale.ROOT).split("[_\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? token : builder.toString();
    }
}
