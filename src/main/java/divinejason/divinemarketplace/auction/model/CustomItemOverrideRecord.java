package divinejason.divinemarketplace.auction.model;

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
