package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates custom item definition state values used by marketplace services, persistence, commands, and GUI rendering.
 */
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
