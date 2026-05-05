package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable custom item override record data between marketplace services, persistence stores, commands, and GUI rendering.
 */
public record CustomItemOverrideRecord(
        String signature,
        String mode,
        String note,
        long createdAtEpochMillis
) {
    public boolean treatAsVanilla() {
        return "TREAT_AS_VANILLA".equalsIgnoreCase(mode);
    }
}
