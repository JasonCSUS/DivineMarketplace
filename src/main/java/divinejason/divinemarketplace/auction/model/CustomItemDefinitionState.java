package divinejason.divinemarketplace.auction.model;

public enum CustomItemDefinitionState {
    CONFIRMED,
    PROVISIONAL;

    public boolean provisional() {
        return this == PROVISIONAL;
    }

    public static CustomItemDefinitionState fromStoredValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CustomItemDefinitionState.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
