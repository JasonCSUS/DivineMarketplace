package divinejason.divinemarketplace.auction.service;


/*
 * File role: Contains marketplace service logic for custom item collision log service.
 */
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
        this.logFile = dataFolder.resolve("logs").resolve("custom_item_collisions.yml");
    }

    public synchronized void recordCollision(String collisionType, CustomItemDefinition existing, CustomItemDefinition incoming, String action) {
        String collisionKey = buildCollisionKey(collisionType, existing, incoming);
        try {
            Files.createDirectories(logFile.getParent());
            if (alreadyLogged(collisionKey)) {
                return;
            }
            Files.writeString(logFile, renderYamlEntry(collisionKey, collisionType, existing, incoming, action), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write custom item collision log.", exception);
        }
    }

    public synchronized List<String> readPageNewestFirst(int page, int pageSize) {
        if (pageSize <= 0) {
            return List.of();
        }
        List<CollisionLogEntry> entries = readEntriesNewestFirst();
        int start = Math.max(0, page) * pageSize;
        if (start >= entries.size()) {
            return List.of();
        }
        int end = Math.min(entries.size(), start + pageSize);
        return entries.subList(start, end).stream().map(this::formatEntryForChat).toList();
    }

    public synchronized int countEntries() {
        return readEntriesNewestFirst().size();
    }

    public Path logFile() {
        return logFile;
    }

    private List<CollisionLogEntry> readEntriesNewestFirst() {
        if (!Files.exists(logFile)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(logFile);
            List<CollisionLogEntry> entries = new ArrayList<>();
            CollisionLogEntryBuilder current = null;
            for (String line : lines) {
                if (line.startsWith("- timestamp:")) {
                    if (current != null) {
                        entries.add(current.build());
                    }
                    current = new CollisionLogEntryBuilder();
                    current.timestamp = unquote(valueAfterColon(line));
                    continue;
                }
                if (current == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.startsWith("type:")) {
                    current.type = unquote(valueAfterColon(trimmed));
                } else if (trimmed.startsWith("action:")) {
                    current.action = unquote(valueAfterColon(trimmed));
                } else if (trimmed.startsWith("itemType:")) {
                    String value = unquote(valueAfterColon(trimmed));
                    if (current.inIncomingBlock) {
                        current.incomingItemType = value;
                    } else {
                        current.existingItemType = value;
                    }
                } else if (trimmed.startsWith("existing:")) {
                    current.inIncomingBlock = false;
                } else if (trimmed.startsWith("incoming:")) {
                    current.inIncomingBlock = true;
                }
            }
            if (current != null) {
                entries.add(current.build());
            }
            Collections.reverse(entries);
            return entries;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read custom item collision log.", exception);
        }
    }

    private boolean alreadyLogged(String collisionKey) throws IOException {
        if (!Files.exists(logFile)) {
            return false;
        }
        String needle = "collisionKey: " + yamlQuote(collisionKey);
        for (String line : Files.readAllLines(logFile)) {
            if (line.trim().equals(needle)) {
                return true;
            }
        }
        return false;
    }

    private String renderYamlEntry(String collisionKey, String collisionType, CustomItemDefinition existing, CustomItemDefinition incoming, String action) {
        String line = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        builder.append("- timestamp: ").append(yamlQuote(LOG_TIME.format(Instant.now()))).append(line);
        builder.append("  collisionKey: ").append(yamlQuote(collisionKey)).append(line);
        builder.append("  type: ").append(yamlQuote(safe(collisionType))).append(line);
        builder.append("  action: ").append(yamlQuote(safe(action))).append(line);
        appendDefinition(builder, "existing", existing, line);
        appendDefinition(builder, "incoming", incoming, line);
        return builder.toString();
    }

    private void appendDefinition(StringBuilder builder, String sectionName, CustomItemDefinition definition, String line) {
        builder.append("  ").append(sectionName).append(':').append(line);
        builder.append("    itemType: ").append(yamlQuote(definition == null ? "" : safe(definition.itemType()))).append(line);
        builder.append("    material: ").append(yamlQuote(definition == null || definition.requiredMaterial() == null ? "" : definition.requiredMaterial().name())).append(line);
        builder.append("    customModelData: ").append(yamlQuote(definition == null || definition.requiredCustomModelData() == null ? "" : Float.toString(definition.requiredCustomModelData()))).append(line);
        builder.append("    marketDisplayName: ").append(yamlQuote(definition == null ? "" : safe(definition.marketDisplayName()))).append(line);
        builder.append("    categoryId: ").append(yamlQuote(definition == null ? "" : safe(definition.categoryId()))).append(line);
        builder.append("    state: ").append(yamlQuote(definition == null || definition.state() == null ? "" : definition.state().name())).append(line);
    }

    private String formatEntryForChat(CollisionLogEntry entry) {
        return entry.timestamp()
                + " | " + entry.type()
                + " | " + entry.action()
                + " | existing=" + entry.existingItemType()
                + " | incoming=" + entry.incomingItemType();
    }

    private String valueAfterColon(String line) {
        int index = line.indexOf(':');
        return index < 0 ? "" : line.substring(index + 1).trim();
    }

    private String unquote(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length() - 1) == '"') {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
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

    private String yamlQuote(String value) {
        return "\"" + safe(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private record CollisionLogEntry(String timestamp, String type, String action, String existingItemType, String incomingItemType) {
    }

    private static final class CollisionLogEntryBuilder {
        private String timestamp = "";
        private String type = "";
        private String action = "";
        private String existingItemType = "";
        private String incomingItemType = "";
        private boolean inIncomingBlock;

        private CollisionLogEntry build() {
            return new CollisionLogEntry(timestamp, type, action, existingItemType, incomingItemType);
        }
    }
}
