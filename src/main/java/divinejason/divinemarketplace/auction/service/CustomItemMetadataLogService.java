package divinejason.divinemarketplace.auction.service;


/*
 * File role: Contains marketplace service logic for custom item metadata log service.
 */
import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import divinejason.divinemarketplace.config.ConfigService;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public final class CustomItemMetadataLogService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());
    private final Path dataFolder;

    public CustomItemMetadataLogService(Path dataFolder) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
    }

    public Path writeSnapshot(ItemStack itemStack, CustomItemTypeExtractionResult result, String reason) {
        return writeSnapshotInternal(itemStack, result, reason, false);
    }

    public Path writeSnapshotIfAbsent(ItemStack itemStack, CustomItemTypeExtractionResult result, String reason) {
        return writeSnapshotInternal(itemStack, result, reason, true);
    }

    private Path writeSnapshotInternal(ItemStack itemStack, CustomItemTypeExtractionResult result, String reason, boolean dedupeBySignature) {
        SerializedItemSignalView view = SerializedItemSignalView.from(itemStack);

        try {
            Path directory = dataFolder.resolve(ConfigService.get().metadataSnapshotDirectory());
            Files.createDirectories(directory);

            String stem = sanitizeFileToken(result != null && result.signature() != null ? result.signature() : itemStack.getType().name().toLowerCase());
            Path output;
            if (dedupeBySignature) {
                output = directory.resolve("unknown_" + stem + ".txt");
                if (Files.exists(output)) {
                    return output;
                }
            } else {
                output = directory.resolve(FILE_TIME.format(Instant.now()) + "_" + stem + ".txt");
            }

            List<String> lines = view.toDetailedLines(result);
            lines.add(0, "reason: " + (reason == null ? "(none)" : reason));
            Files.write(output, lines);
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write custom item metadata snapshot.", exception);
        }
    }

    private String sanitizeFileToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
