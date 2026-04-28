package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CustomItemDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CustomItemCollisionLogService {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    private final Path logFile;

    public CustomItemCollisionLogService(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        this.logFile = dataFolder.resolve("logs").resolve("custom_item_collisions.log");
    }

    public synchronized void recordCollision(String collisionType, CustomItemDefinition existing, CustomItemDefinition incoming, String action) {
        String collisionKey = buildCollisionKey(collisionType, existing, incoming);
        try {
            Files.createDirectories(logFile.getParent());
            if (alreadyLogged(collisionKey)) {
                return;
            }
            String line = LOG_TIME.format(Instant.now())
                    + " | collisionKey=" + collisionKey
                    + " | type=" + safe(collisionType)
                    + " | action=" + safe(action)
                    + " | existing=" + describe(existing)
                    + " | incoming=" + describe(incoming);
            Files.writeString(logFile, line + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write custom item collision log.", exception);
        }
    }

    public synchronized List<String> readPageNewestFirst(int page, int pageSize) {
        if (pageSize <= 0) {
            return List.of();
        }
        if (!Files.exists(logFile)) {
            return List.of();
        }
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(logFile));
            Collections.reverse(lines);
            int start = Math.max(0, page) * pageSize;
            if (start >= lines.size()) {
                return List.of();
            }
            int end = Math.min(lines.size(), start + pageSize);
            return List.copyOf(lines.subList(start, end));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read custom item collision log.", exception);
        }
    }

    public synchronized int countEntries() {
        if (!Files.exists(logFile)) {
            return 0;
        }
        try {
            return Files.readAllLines(logFile).size();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read custom item collision log.", exception);
        }
    }

    public Path logFile() {
        return logFile;
    }

    private boolean alreadyLogged(String collisionKey) throws IOException {
        if (!Files.exists(logFile)) {
            return false;
        }
        String needle = "collisionKey=" + collisionKey;
        for (String line : Files.readAllLines(logFile)) {
            if (line.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String buildCollisionKey(String collisionType, CustomItemDefinition existing, CustomItemDefinition incoming) {
        return safe(collisionType)
                + "|existing=" + identityKey(existing)
                + "|incoming=" + identityKey(incoming);
    }

    private String identityKey(CustomItemDefinition definition) {
        if (definition == null) {
            return "(none)";
        }
        return safe(definition.itemType()) + "@" + definition.requiredMaterial() + ":" + definition.requiredCustomModelData();
    }

    private String describe(CustomItemDefinition definition) {
        if (definition == null) {
            return "(none)";
        }
        return "itemType=" + safe(definition.itemType())
                + ", material=" + definition.requiredMaterial()
                + ", cmd=" + definition.requiredCustomModelData()
                + ", display=" + safe(definition.marketDisplayName())
                + ", category=" + safe(definition.categoryId())
                + ", state=" + definition.state();
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "(none)";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
