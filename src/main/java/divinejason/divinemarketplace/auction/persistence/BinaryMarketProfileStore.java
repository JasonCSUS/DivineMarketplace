package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.MarketProfile;
import divinejason.divinemarketplace.auction.repository.MarketProfileRepository;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary persistence for compact market profile cache state.
 */
public final class BinaryMarketProfileStore implements MarketProfileRepository {
    private static final String MAGIC = "DMPROFILE";
    private static final int VERSION = 1;

    private final Path filePath;
    private final Object lock = new Object();

    public BinaryMarketProfileStore(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve(PluginDirectoryLayout.DATA_MARKET_PROFILES));
    }

    public BinaryMarketProfileStore(Path filePath) {
        this.filePath = filePath;
        try {
            BinaryStoreSupport.ensureFileExists(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize market profiles file: " + filePath, exception);
        }
    }

    @Override
    public MarketProfile getOrDefault(String marketKey) {
        synchronized (lock) {
            Map<String, MarketProfile> profiles = loadAll();
            return profiles.getOrDefault(
                    marketKey,
                    new MarketProfile(marketKey, 0L, 0L, 0L, 0.0, 0.0, 0, 0, 0L)
            );
        }
    }

    @Override
    public void save(MarketProfile marketProfile) {
        synchronized (lock) {
            Map<String, MarketProfile> profiles = loadAll();
            profiles.put(marketProfile.marketKey(), marketProfile);
            saveAll(profiles);
        }
    }

    @Override
    public Iterable<MarketProfile> getAllProfiles() {
        synchronized (lock) {
            return List.copyOf(loadAll().values());
        }
    }

    private Map<String, MarketProfile> loadAll() {
        try {
            if (BinaryStoreSupport.isEmptyFile(filePath)) {
                return new LinkedHashMap<>();
            }

            try (DataInputStream input = BinaryStoreSupport.newInput(filePath)) {
                BinaryStoreSupport.requireHeader(input, MAGIC, VERSION);

                int count = input.readInt();
                Map<String, MarketProfile> profiles = new LinkedHashMap<>(Math.max(16, count));

                for (int i = 0; i < count; i++) {
                    String marketKey = BinaryStoreSupport.readString(input);
                    long currentRecommended = input.readLong();
                    long averageSale = input.readLong();
                    long averageListing = input.readLong();
                    double slope = input.readDouble();
                    double fitness = input.readDouble();
                    int saleSamples = input.readInt();
                    int listingSamples = input.readInt();
                    long lastRecalc = input.readLong();

                    if (marketKey != null) {
                        profiles.put(marketKey, new MarketProfile(
                                marketKey,
                                currentRecommended,
                                averageSale,
                                averageListing,
                                slope,
                                fitness,
                                saleSamples,
                                listingSamples,
                                lastRecalc
                        ));
                    }
                }

                return profiles;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read market profiles from " + filePath, exception);
        }
    }

    private void saveAll(Map<String, MarketProfile> profiles) {
        try {
            BinaryStoreSupport.writeToTempFile(filePath, output -> writeProfiles(output, profiles));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write market profiles to " + filePath, exception);
        }
    }

    private void writeProfiles(DataOutputStream output, Map<String, MarketProfile> profiles) {
        try {
            BinaryStoreSupport.writeHeader(output, MAGIC, VERSION);
            output.writeInt(profiles.size());

            for (MarketProfile profile : profiles.values()) {
                BinaryStoreSupport.writeString(output, profile.marketKey());
                output.writeLong(profile.currentRecommendedUnitPrice());
                output.writeLong(profile.averageSaleUnitPrice());
                output.writeLong(profile.averageListingUnitPrice());
                output.writeDouble(profile.recentSaleSlope());
                output.writeDouble(profile.recentSaleSlopeFitness());
                output.writeInt(profile.recentSaleSampleCount());
                output.writeInt(profile.recentListingSampleCount());
                output.writeLong(profile.lastRecalculatedAtEpochMillis());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed while encoding market profiles.", exception);
        }
    }
}
