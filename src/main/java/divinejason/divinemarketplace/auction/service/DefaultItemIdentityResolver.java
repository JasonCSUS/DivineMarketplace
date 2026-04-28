package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.BrowseVisibility;
import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import divinejason.divinemarketplace.auction.model.CustomItemDefinitionState;
import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import divinejason.divinemarketplace.auction.model.MarketHistoryParticipation;
import divinejason.divinemarketplace.auction.model.MarketTrainingParticipation;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import divinejason.divinemarketplace.auction.model.ReviewFlagLevel;
import divinejason.divinemarketplace.config.ConfigService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class DefaultItemIdentityResolver implements ItemIdentityResolver {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final CategoryResolver categoryResolver;
    private final CustomItemRegistry customItemRegistry;
    private final CustomItemTypeExtractor customItemTypeExtractor;
    private final CustomItemMetadataLogService metadataLogService;

    public DefaultItemIdentityResolver(
            CategoryResolver categoryResolver,
            CustomItemRegistry customItemRegistry,
            CustomItemTypeExtractor customItemTypeExtractor,
            CustomItemMetadataLogService metadataLogService
    ) {
        this.categoryResolver = Objects.requireNonNull(categoryResolver, "categoryResolver");
        this.customItemRegistry = Objects.requireNonNull(customItemRegistry, "customItemRegistry");
        this.customItemTypeExtractor = Objects.requireNonNull(customItemTypeExtractor, "customItemTypeExtractor");
        this.metadataLogService = Objects.requireNonNull(metadataLogService, "metadataLogService");
    }

    @Override
    public ResolvedItemDefinition resolve(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            throw new IllegalArgumentException("itemStack cannot be null or air");
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (isFilledPackage(itemStack, itemMeta)) {
            return buildPackageDefinition(itemStack);
        }

        CustomItemTypeExtractionResult extraction = customItemTypeExtractor.inspect(itemStack);
        if (extraction != null && extraction.treatAsVanilla()) {
            return buildVanillaDefinition(itemStack);
        }

        if (extraction != null && extraction.requiredCustomModelData() != null) {
            CustomItemDefinition existingFallbackDefinition = customItemRegistry.findByMaterialAndCustomModelData(
                    itemStack.getType(),
                    extraction.requiredCustomModelData()
            );
            if (existingFallbackDefinition != null) {
                return buildResolvedFromDefinition(existingFallbackDefinition);
            }
        }

        if (extraction != null && (extraction.hasCustomSignal() || extraction.requiredCustomModelData() != null)) {
            return buildCustomDefinition(itemStack, extraction);
        }

        if (itemStack.getType() == Material.ENCHANTED_BOOK
                && itemMeta instanceof EnchantmentStorageMeta storageMeta
                && storageMeta.hasStoredEnchants()) {
            return buildEnchantedBookDefinition(storageMeta);
        }

        if (isPotionLike(itemStack, itemMeta)) {
            return buildPotionDefinition(itemStack, (PotionMeta) itemMeta);
        }

        return buildVanillaDefinition(itemStack);
    }

    private ResolvedItemDefinition buildCustomDefinition(ItemStack itemStack, CustomItemTypeExtractionResult extraction) {
        Material material = itemStack.getType();
        Float customModelData = extraction.requiredCustomModelData();
        String displayName = chooseDisplayName(itemStack);

        CustomItemDefinition definition = null;
        if (extraction.itemType() != null && !extraction.itemType().isBlank()) {
            definition = customItemRegistry.findByItemType(extraction.itemType());
        }
        if (definition == null && customModelData != null) {
            definition = customItemRegistry.findByMaterialAndCustomModelData(material, customModelData);
        }

        boolean provisional = extraction.provisional();

        boolean shouldAutoCreate = ConfigService.get().getMainConfig().customItems().autoWriteDefinitionsImmediately()
                && (extraction.hasCustomSignal()
                || (customModelData != null && ConfigService.get().autoCreateUnknownCustomModelDefinitions()));

        if (definition == null && shouldAutoCreate) {
            String itemType = extraction.itemType() != null && !extraction.itemType().isBlank()
                    ? extraction.itemType()
                    : generatedFallbackItemType(material, customModelData);

            String category = provisional
                    ? ConfigService.get().unknownCustomModelCategory()
                    : ConfigService.get().getMainConfig().customItems().defaultCategory();

            definition = customItemRegistry.upsertDefinition(
                    new CustomItemDefinition(itemType, material, customModelData, displayName, category, provisional ? CustomItemDefinitionState.PROVISIONAL : CustomItemDefinitionState.CONFIRMED)
            );

            if (provisional && ConfigService.get().writeUnknownMetadataSnapshots()) {
                metadataLogService.writeSnapshotIfAbsent(itemStack, extraction, "unknown_custom_autodefined");
            }
        } else if (definition == null && provisional && ConfigService.get().writeUnknownMetadataSnapshots()) {
            metadataLogService.writeSnapshotIfAbsent(itemStack, extraction, "unknown_custom_detected_no_definition");
        }

        if (definition != null) {
            return buildResolvedFromDefinition(definition);
        }

        String oddityKey = extraction.itemType() != null && !extraction.itemType().isBlank()
                ? extraction.itemType()
                : "oddity:" + material.name().toLowerCase(Locale.ROOT);

        return new ResolvedItemDefinition(
                oddityKey,
                displayName,
                "unsorted",
                oddityKey,
                !provisional,
                provisional ? MarketHistoryParticipation.EXCLUDED : MarketHistoryParticipation.INCLUDED,
                provisional ? MarketTrainingParticipation.EXCLUDED : MarketTrainingParticipation.INCLUDED,
                provisional ? BrowseVisibility.RECENT_ONLY : BrowseVisibility.FULLY_SORTED,
                provisional ? ReviewFlagLevel.HIGH_PRIORITY : ReviewFlagLevel.NORMAL,
                Map.of()
        );
    }

    private ResolvedItemDefinition buildResolvedFromDefinition(CustomItemDefinition definition) {
        boolean provisional = definition.provisional();
        return new ResolvedItemDefinition(
                definition.itemType(),
                definition.marketDisplayName(),
                definition.categoryId(),
                definition.itemType(),
                !provisional,
                provisional ? MarketHistoryParticipation.EXCLUDED : MarketHistoryParticipation.INCLUDED,
                provisional ? MarketTrainingParticipation.EXCLUDED : MarketTrainingParticipation.INCLUDED,
                provisional ? BrowseVisibility.RECENT_ONLY : BrowseVisibility.FULLY_SORTED,
                provisional ? ReviewFlagLevel.HIGH_PRIORITY : ReviewFlagLevel.NORMAL,
                Map.of()
        );
    }

    private ResolvedItemDefinition buildEnchantedBookDefinition(EnchantmentStorageMeta storageMeta) {
        Map<Enchantment, Integer> stored = new TreeMap<>((left, right) -> enchantKey(left).compareTo(enchantKey(right)));
        stored.putAll(storageMeta.getStoredEnchants());
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            enchantments.put(enchantKey(entry.getKey()), entry.getValue());
        }

        boolean mixed = enchantments.size() > 1;
        String marketKey;
        String marketDisplayName;
        boolean recommendationEnabled;
        MarketTrainingParticipation trainingParticipation;
        if (mixed) {
            StringBuilder keyBuilder = new StringBuilder("enchanted_book:mixed");
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                keyBuilder.append("|").append(entry.getKey()).append(":").append(entry.getValue());
            }
            marketKey = keyBuilder.toString();
            marketDisplayName = "Mixed Enchants";
            recommendationEnabled = false;
            trainingParticipation = MarketTrainingParticipation.EXCLUDED;
        } else {
            Map.Entry<String, Integer> only = enchantments.entrySet().iterator().next();
            marketKey = "enchanted_book:" + only.getKey() + ":" + only.getValue();
            marketDisplayName = humanizeToken(stripNamespace(only.getKey())) + " " + toRoman(only.getValue()) + " Book";
            recommendationEnabled = true;
            trainingParticipation = MarketTrainingParticipation.INCLUDED;
        }

        return new ResolvedItemDefinition(
                marketKey,
                marketDisplayName,
                "enchanted_books",
                null,
                recommendationEnabled,
                MarketHistoryParticipation.INCLUDED,
                trainingParticipation,
                BrowseVisibility.FULLY_SORTED,
                ReviewFlagLevel.NONE,
                enchantments
        );
    }

    private ResolvedItemDefinition buildPackageDefinition(ItemStack itemStack) {
        String materialName = itemStack.getType().name().toLowerCase(Locale.ROOT);
        return new ResolvedItemDefinition(
                "package:" + materialName,
                chooseDisplayName(itemStack),
                categoryResolver.resolveCategoryId(itemStack),
                null,
                false,
                MarketHistoryParticipation.EXCLUDED,
                MarketTrainingParticipation.EXCLUDED,
                BrowseVisibility.RECENT_ONLY,
                ReviewFlagLevel.NONE,
                Map.of()
        );
    }

    private ResolvedItemDefinition buildPotionDefinition(ItemStack itemStack, PotionMeta potionMeta) {
        String prefix = switch (itemStack.getType()) {
            case SPLASH_POTION -> "Splash Potion";
            case LINGERING_POTION -> "Lingering Potion";
            case TIPPED_ARROW -> "Tipped Arrow";
            default -> "Potion";
        };

        List<String> displayParts = new ArrayList<>();
        List<String> keyParts = new ArrayList<>();
        if (potionMeta.hasBasePotionType()) {
            PotionType baseType = potionMeta.getBasePotionType();
            if (baseType != null) {
                displayParts.add(humanizeToken(baseType.name()));
                keyParts.add(baseType.name().toLowerCase(Locale.ROOT));
            }
        }
        if (potionMeta.hasCustomEffects()) {
            for (PotionEffect effect : potionMeta.getCustomEffects()) {
                PotionEffectType type = effect.getType();
                String effectKey = effectTypeKey(type);
                keyParts.add(effectKey + ":" + (effect.getAmplifier() + 1));
                displayParts.add(humanizeToken(stripNamespace(effectKey)) + " " + toRoman(effect.getAmplifier() + 1));
            }
        }
        if (potionMeta.hasCustomPotionName()) {
            String customPotionName = potionMeta.getCustomPotionName();
            if (customPotionName != null && !customPotionName.isBlank()) {
                displayParts.add(customPotionName);
                keyParts.add("named:" + customPotionName.toLowerCase(Locale.ROOT));
            }
        }

        String descriptor = displayParts.isEmpty() ? "Unspecified" : String.join(", ", displayParts);
        String keySuffix = keyParts.isEmpty() ? "unspecified" : String.join("|", keyParts);
        return new ResolvedItemDefinition(
                "potion:" + itemStack.getType().name().toLowerCase(Locale.ROOT) + ":" + keySuffix,
                prefix + " of " + descriptor,
                "potions",
                null,
                true,
                MarketHistoryParticipation.INCLUDED,
                MarketTrainingParticipation.INCLUDED,
                BrowseVisibility.FULLY_SORTED,
                ReviewFlagLevel.NONE,
                Map.of()
        );
    }

    private ResolvedItemDefinition buildVanillaDefinition(ItemStack itemStack) {
        String marketKey = "vanilla:" + itemStack.getType().name().toLowerCase(Locale.ROOT);
        return new ResolvedItemDefinition(
                marketKey,
                chooseDisplayName(itemStack),
                categoryResolver.resolveCategoryId(itemStack),
                null,
                true,
                MarketHistoryParticipation.INCLUDED,
                MarketTrainingParticipation.INCLUDED,
                BrowseVisibility.FULLY_SORTED,
                ReviewFlagLevel.NONE,
                Map.of()
        );
    }

    private boolean isPotionLike(ItemStack itemStack, ItemMeta itemMeta) {
        return itemMeta instanceof PotionMeta
                && (itemStack.getType() == Material.POTION
                || itemStack.getType() == Material.SPLASH_POTION
                || itemStack.getType() == Material.LINGERING_POTION
                || itemStack.getType() == Material.TIPPED_ARROW);
    }

    private boolean isFilledPackage(ItemStack itemStack, ItemMeta itemMeta) {
        if (itemMeta instanceof BundleMeta bundleMeta) {
            if (!bundleMeta.hasItems()) {
                return false;
            }
            for (ItemStack nested : bundleMeta.getItems()) {
                if (nested != null && !nested.getType().isAir() && nested.getAmount() > 0) {
                    return true;
                }
            }
            return false;
        }
        if (itemMeta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
            for (ItemStack nested : shulkerBox.getSnapshotInventory().getContents()) {
                if (nested != null && !nested.getType().isAir() && nested.getAmount() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private String chooseDisplayName(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            String name = componentToPlain(meta.customName());
            if (name != null) {
                return name;
            }
            name = componentToPlain(meta.displayName());
            if (name != null) {
                return name;
            }
            name = componentToPlain(meta.itemName());
            if (name != null) {
                return name;
            }
        }
        return humanizeToken(itemStack.getType().name());
    }

    private String componentToPlain(Component component) {
        if (component == null) {
            return null;
        }
        String plain = PLAIN.serialize(component).trim();
        return plain.isBlank() ? null : plain;
    }

    private String generatedFallbackItemType(Material material, Float customModelData) {
        if (customModelData == null) {
            return "auto_" + material.name().toLowerCase(Locale.ROOT);
        }
        String cmd = Float.toString(customModelData).replace('.', '_');
        return "auto_" + material.name().toLowerCase(Locale.ROOT) + "_" + cmd;
    }

    private String enchantKey(Enchantment enchantment) {
        NamespacedKey key = enchantment.getKey();
        return key.getNamespace() + ":" + key.getKey();
    }

    private String effectTypeKey(PotionEffectType potionEffectType) {
        NamespacedKey key = potionEffectType.getKey();
        return key.getNamespace() + ":" + key.getKey();
    }

    private String stripNamespace(String key) {
        int index = key.indexOf(':');
        return index >= 0 ? key.substring(index + 1) : key;
    }

    private String humanizeToken(String token) {
        String[] parts = token.toLowerCase(Locale.ROOT).split("[_\s]+");
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

    private String toRoman(int value) {
        if (value <= 0) {
            return Integer.toString(value);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                remaining -= values[i];
                builder.append(numerals[i]);
            }
        }
        return builder.toString();
    }
}
