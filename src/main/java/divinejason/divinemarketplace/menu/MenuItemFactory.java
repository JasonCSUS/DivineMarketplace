package divinejason.divinemarketplace.menu;

import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.EnchantBrowseSummary;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.model.SortMode;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Central GUI item builder backed by editable menu.yml visual keys. */
public final class MenuItemFactory {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MenuVisualConfig visuals;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MenuItemFactory(MenuVisualConfig visuals) {
        this.visuals = visuals;
    }

    public ItemStack backButton() { return configured("back", Material.ARROW, "<yellow>Back</yellow>"); }
    public ItemStack previousPageButton() { return configured("previousPage", Material.ARROW, "<yellow>Previous Page</yellow>"); }
    public ItemStack nextPageButton() { return configured("nextPage", Material.ARROW, "<yellow>Next Page</yellow>"); }
    public ItemStack previousMonthButton() { return configured("previousMonth", Material.ARROW, "<yellow>Previous Month</yellow>", List.of("<gray>Move to the next older month with price data.</gray>")); }
    public ItemStack nextMonthButton() { return configured("nextMonth", Material.ARROW, "<yellow>Next Month</yellow>", List.of("<gray>Move to the next newer month with price data.</gray>")); }
    public ItemStack searchButton() { return configured("search", Material.COMPASS, "<aqua>Search</aqua>", List.of("<gray>Closes the menu and asks for search text in chat.</gray>", "<dark_gray>You can also use /market search &lt;query&gt;.</dark_gray>")); }
    public ItemStack listHeldItemButton() { return configured("listHeldItem", Material.ANVIL, "<green>List Held Item</green>", List.of("<gray>Closes the menu and asks for quantity + unit price in chat.</gray>", "<dark_gray>Example: 32 150</dark_gray>")); }
    public ItemStack saleHistoryButton() { return configured("saleHistory", Material.BOOK, "<gold>Sale History</gold>"); }
    public ItemStack priceHistoryButton() { return configured("priceHistory", Material.CLOCK, "<gold>Price History</gold>"); }
    public ItemStack fillerPurple() { return configured("fillerPurple", Material.PURPLE_STAINED_GLASS_PANE, " "); }
    public ItemStack fillerBlack() { return configured("fillerBlack", Material.BLACK_STAINED_GLASS_PANE, " "); }
    public ItemStack lockedRed() { return configured("lockedRed", Material.RED_STAINED_GLASS_PANE, "<red>Unavailable</red>"); }
    public ItemStack increaseArrow() { return configured("increase", Material.LIME_DYE, "<green>Increase Quantity</green>"); }
    public ItemStack decreaseArrow() { return configured("decrease", Material.RED_DYE, "<red>Decrease Quantity</red>"); }
    public ItemStack cancelListingButton() { return configured("cancelListing", Material.BARRIER, "<red>Cancel Listing</red>"); }
    public ItemStack claimOneChunkButton(ItemClaimRecord claim) { return configured("claimOneChunk", Material.CHEST, "<green>Claim One Stack</green>", List.of("<gray>Claims up to one safe stack.</gray>", "<gray>Remaining:</gray> <white>" + claim.amount() + "</white>")); }
    public ItemStack claimAsMuchAsFitsButton(ItemClaimRecord claim) { return configured("claimAsMuchAsFits", Material.ENDER_CHEST, "<green>Claim As Much As Fits</green>", List.of("<gray>Fills available inventory space safely.</gray>", "<gray>Remaining:</gray> <white>" + claim.amount() + "</white>")); }
    public ItemStack relistClaimButton(ItemClaimRecord claim) { return configured("relistClaim", Material.EMERALD, "<green>Relist Claim</green>", List.of("<gray>Closes the menu and asks for quantity + unit price in chat.</gray>", "<gray>Available:</gray> <white>" + claim.amount() + "</white>", "<dark_gray>Examples: 32 150, stack 150, all 150</dark_gray>")); }

    public ItemStack claimEarningsButton(long balance) {
        return configured("claimEarnings", Material.GOLD_INGOT, "<gold>Claim Earnings</gold>", List.of("<gray>Pending:</gray> <yellow>$" + formatMoney(balance) + "</yellow>"));
    }

    public ItemStack sortButton(SortMode currentMode) {
        String mode = currentMode == null ? "NEWEST_FIRST" : currentMode.name();
        return configured("sort", Material.HOPPER, "<yellow>Sort</yellow>", List.of("<gray>Current:</gray> <white>" + pretty(mode) + "</white>", "<dark_gray>Click to cycle.</dark_gray>"));
    }

    public ItemStack categoryItem(CategorySummary summary) {
        Material material = materialFromKey(summary.iconKey(), fallbackCategoryMaterial(summary.categoryId()));
        return configuredDynamic("category." + summary.categoryId(), material, "<gold>" + escape(summary.displayName()) + "</gold>", List.of("<gray>Active listings:</gray> <white>" + summary.activeListingCount() + "</white>", "<dark_gray>Click to browse category.</dark_gray>"));
    }

    public ItemStack marketGroupItem(SubcategorySummary summary) {
        Material material = materialFromKey(summary.previewIconKey(), Material.CHEST);
        return configuredDynamic("marketGroup", material, "<white>" + escape(summary.marketDisplayName()) + "</white>", List.of("<gray>Listed amount:</gray> <white>" + summary.listedQuantity() + "</white>", "<dark_gray>Click to view active listings.</dark_gray>"));
    }

    public ItemStack enchantTargetItem(EnchantBrowseSummary summary) {
        return configuredDynamic("enchantTarget", Material.ENCHANTED_BOOK, "<light_purple>" + escape(summary.displayName()) + "</light_purple>", List.of("<gray>Book groups:</gray> <white>" + summary.activeMarketGroupCount() + "</white>", "<gray>Listed amount:</gray> <white>" + summary.listedQuantity() + "</white>", "<dark_gray>Click to browse this enchant target.</dark_gray>"));
    }

    public ItemStack listingItem(Listing listing) {
        ItemStack item = listing.listedItemSnapshot().clone();
        item.setAmount(Math.max(1, Math.min(listing.amount(), Math.max(1, item.getMaxStackSize()))));
        appendLoreAndName(item, "<white>" + escape(listing.marketDisplayName()) + "</white>", List.of("<gray>Amount:</gray> <white>" + listing.amount() + "</white>", "<gray>Unit price:</gray> <yellow>$" + formatMoney(listing.unitPrice()) + "</yellow>", "<gray>Total:</gray> <yellow>$" + formatMoney(listing.unitPrice() * Math.max(1, listing.amount())) + "</yellow>", "<dark_gray>Click to refresh and inspect this listing.</dark_gray>"));
        return item;
    }

    public ItemStack listingUnavailableItem() {
        return configured("listingUnavailable", Material.BARRIER, "<red>Listing unavailable</red>", List.of("<gray>This listing was sold, cancelled, or expired.</gray>", "<dark_gray>Use Back to return to the previous list.</dark_gray>"));
    }

    public ItemStack ownerListingInfo(Listing listing) {
        return configuredDynamic("ownerListingInfo", Material.PAPER, "<yellow>Your Listing</yellow>", List.of("<gray>This is your active listing.</gray>", "<gray>Amount:</gray> <white>" + listing.amount() + "</white>", "<gray>Unit price:</gray> <yellow>$" + formatMoney(listing.unitPrice()) + "</yellow>", "<dark_gray>You can cancel it from this view.</dark_gray>"));
    }

    public ItemStack claimItem(ItemClaimRecord claim) {
        ItemStack item = claim.claimItemSnapshot().clone();
        item.setAmount(Math.max(1, Math.min(claim.amount(), Math.max(1, item.getMaxStackSize()))));
        appendLoreAndName(item, null, List.of("<gray>Claim amount:</gray> <white>" + claim.amount() + "</white>", "<dark_gray>Click to open claim actions.</dark_gray>"));
        return item;
    }

    public ItemStack saleHistoryItem(SaleRecord sale) {
        return configuredDynamic("saleHistoryEntry", Material.PAPER, "<gold>Sale</gold>", List.of("<gray>Amount:</gray> <white>" + sale.amountPurchased() + "</white>", "<gray>Unit price:</gray> <yellow>$" + formatMoney(sale.unitPrice()) + "</yellow>", "<gray>Time:</gray> <white>" + formatDateTime(sale.soldAtEpochMillis()) + "</white>"));
    }

    public ItemStack priceHistoryItem(RecommendationHistoryPoint point) {
        return configuredDynamic("priceHistoryEntry", Material.PAPER, "<gold>Price Point</gold>", List.of("<gray>Price:</gray> <yellow>$" + formatMoney(point.recommendedUnitPrice()) + "</yellow>", "<gray>Time:</gray> <white>" + formatDateTime(point.recordedAtEpochMillis()) + "</white>"));
    }

    public ItemStack quantityPaper(int quantity, long unitPrice, int availableAmount) {
        return configuredDynamic("quantity", Material.PAPER, "<yellow>Quantity: " + quantity + "</yellow>", List.of("<gray>Available:</gray> <white>" + availableAmount + "</white>", "<gray>Total:</gray> <yellow>$" + formatMoney(unitPrice * Math.max(1, quantity)) + "</yellow>"));
    }

    public ItemStack confirmPurchaseButton(int quantity, long totalPrice) {
        return configured("confirmPurchase", Material.EMERALD, "<green>Confirm Purchase</green>", List.of("<gray>Quantity:</gray> <white>" + quantity + "</white>", "<gray>Total:</gray> <yellow>$" + formatMoney(totalPrice) + "</yellow>", "<dark_gray>Click to buy into your claim storage.</dark_gray>"));
    }

    public ItemStack monthContextItem(YearMonth month, int monthIndex, int monthCount) {
        return configuredDynamic("monthContext", Material.CLOCK, "<gold>Month</gold>", List.of("<gray>Viewing:</gray> <white>" + prettyMonth(month) + "</white>", "<gray>Data month:</gray> <white>" + (monthIndex + 1) + " / " + monthCount + "</white>"));
    }

    public ItemStack infoItem(String title, List<String> lore) {
        return configuredDynamic("info", Material.PAPER, title, lore);
    }

    private ItemStack configured(String key, Material fallbackMaterial, String fallbackName) {
        return configured(key, fallbackMaterial, fallbackName, List.of());
    }

    private ItemStack configuredDynamic(String key, Material fallbackMaterial, String fallbackName, List<String> extraLore) {
        MenuIconSpec spec = visuals.item(key, fallbackMaterial, fallbackName);
        return buildItem(spec, fallbackName, true, extraLore);
    }

    private ItemStack configured(String key, Material fallbackMaterial, String fallbackName, List<String> extraLore) {
        MenuIconSpec spec = visuals.item(key, fallbackMaterial, fallbackName);
        return buildItem(spec, fallbackName, false, extraLore);
    }

    private ItemStack buildItem(MenuIconSpec spec, String fallbackName, boolean forceFallbackName, List<String> extraLore) {
        ItemStack item = new ItemStack(spec.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(parse(forceFallbackName || spec.name().isBlank() ? fallbackName : spec.name()));
        List<Component> lore = new ArrayList<>();
        for (String line : spec.lore()) {
            lore.add(parse(line));
        }
        for (String line : extraLore) {
            lore.add(parse(line));
        }
        meta.lore(lore);
        if (spec.customModelData() != null) {
            meta.setCustomModelData(spec.customModelData());
        }
        item.setItemMeta(meta);
        return item;
    }

    private void appendLoreAndName(ItemStack item, String nameOrNull, List<String> loreLines) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (nameOrNull != null) {
            meta.displayName(parse(nameOrNull));
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        for (String line : loreLines) {
            lore.add(parse(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private Component parse(String text) {
        return miniMessage.deserialize(text == null ? "" : text);
    }

    private Material fallbackCategoryMaterial(String categoryId) {
        return switch (MenuVisualConfig.normalize(categoryId)) {
            case "building_blocks" -> Material.STONE;
            case "decorative_blocks" -> Material.FLOWER_POT;
            case "tools" -> Material.DIAMOND_PICKAXE;
            case "weapons" -> Material.DIAMOND_SWORD;
            case "armor" -> Material.DIAMOND_CHESTPLATE;
            case "enchanted_books" -> Material.ENCHANTED_BOOK;
            case "food" -> Material.COOKED_BEEF;
            case "farming" -> Material.WHEAT;
            case "ores" -> Material.DIAMOND_ORE;
            case "redstone" -> Material.REDSTONE;
            default -> Material.CHEST;
        };
    }

    private Material materialFromKey(String key, Material fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(key.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private String pretty(String token) {
        if (token == null || token.isBlank()) {
            return "Unknown";
        }
        String[] parts = token.toLowerCase(Locale.ROOT).split("[_: -]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return escape(builder.toString());
    }

    private String escape(String input) {
        return input == null ? "" : input.replace("<", "\\<").replace(">", "\\>");
    }

    private String formatMoney(long hundredths) {
        return String.format(Locale.US, "%.2f", hundredths / 100.0);
    }

    private String formatDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMAT);
    }

    private String prettyMonth(YearMonth month) {
        if (month == null) {
            return "Unknown";
        }
        return month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.US) + " " + month.getYear();
    }
}
