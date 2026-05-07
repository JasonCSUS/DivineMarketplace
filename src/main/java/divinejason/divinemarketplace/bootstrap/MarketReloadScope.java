package divinejason.divinemarketplace.bootstrap;

/**
 * Explicit reload scopes for /market reload.
 *
 * <p>Reload must stay targeted: config/menu/prices can be refreshed without a
 * blocking full runtime SQL reload.  Full storage reloads should use a future
 * dedicated repair/diagnostic command guarded by runtime state.</p>
 */
public enum MarketReloadScope {
    CONFIG,
    MENU,
    PRICES;

    public static MarketReloadScope fromToken(String token) {
        if (token == null || token.isBlank()) {
            return CONFIG;
        }
        return switch (token.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "config" -> CONFIG;
            case "menu", "gui" -> MENU;
            case "price", "prices" -> PRICES;
            default -> throw new IllegalArgumentException("Unknown reload scope: " + token);
        };
    }
}
