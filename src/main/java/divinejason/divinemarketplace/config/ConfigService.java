package divinejason.divinemarketplace.config;

/**
 * Singleton access point for the currently loaded MainConfig.
 *
 * This stays intentionally simple in scaffolding form. Specific helper getters
 * are added here so core logic can depend on clean names rather than drilling
 * through a future config tree.
 */
public final class ConfigService {

    private static final ConfigService INSTANCE = new ConfigService();

    private MainConfig mainConfig;

    private ConfigService() {
    }

    public static ConfigService get() {
        return INSTANCE;
    }

    public void setMainConfig(MainConfig mainConfig) {
        this.mainConfig = mainConfig;
    }

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    // Listing / claims policy
    public long listingDefaultDurationMillis() { return 0L; }
    public int listingDefaultMaxActiveListings() { return 0; }
    public long itemClaimAbandonMillis() { return 0L; }

    // Storage / cleanup
    public int salesHistoryMaxMb() { return 0; }
    public int adminSalesHistoryMaxMb() { return 0; }
    public int adminListingsHistoryMaxMb() { return 0; }
    public int adminClaimsHistoryMaxMb() { return 0; }
    public int itemClaimsSoftMaxMb() { return 0; }

    // Market recalculation
    public long marketRecalcIntervalMillis() { return 0L; }
    public long marketProfileMinimumRecalcMillis() { return 0L; }
    public int marketRecalcItemsPerRun() { return 0; }
    public long marketSaleLookbackMillis() { return 0L; }
    public double marketTrendFitnessThreshold() { return 0.0; }
    public int marketMinimumSaleSamples() { return 0; }
    public int marketMinimumListingSamples() { return 0; }
    public double marketMinimumAdjustmentPercent() { return 0.0; }
    public double marketMaximumAdjustmentPercent() { return 0.0; }
}
