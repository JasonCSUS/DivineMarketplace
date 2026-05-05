package divinejason.divinemarketplace.command;


/*
 * File role: Handles the market enchant admin command subcommand group and keeps its permission checks, parsing, and output in one file.
 */
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MarketEnchantAdminCommand implements MarketAdminCommandHandler {
    private static final Set<String> ENCHANT_TARGET_ALIASES = Set.of(
            "armor", "armour",
            "helmet", "helm", "head", "helmets",
            "chest", "chestplate", "chestplates",
            "legs", "leggings", "pants",
            "boot", "boots",
            "tool", "tools",
            "pickaxe", "pick", "pickaxes",
            "axe", "axes",
            "sword", "swords",
            "hoe", "hoes",
            "shovel", "spade", "shovels",
            "elytra",
            "bow", "bows",
            "crossbow", "crossbows",
            "shield", "shields",
            "shear", "shears",
            "trident", "tridents",
            "universal", "all", "any"
    );

    private final MarketAdminCommandContext context;

    MarketEnchantAdminCommand(MarketAdminCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRoot(String root) {
        return root.equalsIgnoreCase("enchant");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        handleEnchant(sender, args);
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        return args.length == 2 ? context.filterByPrefix(List.of("define"), context.currentToken(args)) : List.of();
    }

    @Override
    public Collection<String> rootSuggestions(CommandSender sender) {
        return context.hasAdminPermission(sender, "divinemarketplace.admin.enchant.define") ? List.of("enchant") : List.of();
    }

    private void handleEnchant(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.enchant.define");
        if (args.length < 4 || !args[1].equalsIgnoreCase("define")) {
            sendEnchantDefineUsage(sender);
            return;
        }

        ParsedEnchantDefinition parsed = parseEnchantDefinitionArgs(args);
        if (!parsed.valid()) {
            sender.sendRichMessage("<red>" + context.escapeMini(parsed.errorMessage()) + "</red>");
            sendEnchantDefineUsage(sender);
            return;
        }

        context.customEnchantStore.upsert(parsed.namespacedEnchantKey(), parsed.displayName(), parsed.itemTokens());
        context.plugin.reloadRuntimeData();
        sender.sendRichMessage("<green>Stored custom enchant definition for</green> <white>" + context.escapeMini(parsed.namespacedEnchantKey())
                + "</white> <gray>targets=" + context.escapeMini(String.join(", ", parsed.itemTokens())) + "</gray>");
    }

    private void sendEnchantDefineUsage(CommandSender sender) {
        sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market enchant define &lt;plugin:enchant&gt; {Display Name} {target1, target2}</gray>");
        sender.sendRichMessage("<dark_gray>Example: /market enchant define excellentenchants:double_strike {Double Strike} {sword, axe}</dark_gray>");
    }

    private ParsedEnchantDefinition parseEnchantDefinitionArgs(String[] args) {
        String namespacedEnchantKey = args.length >= 3 ? args[2].trim().toLowerCase(Locale.ROOT) : "";
        if (!namespacedEnchantKey.contains(":")) {
            return ParsedEnchantDefinition.invalid("Enchant key must be namespaced like plugin:enchant.");
        }

        ParsedCommandFields parsedFields = parseGroupedCommandFields(args, 3);
        if (!parsedFields.valid()) {
            return ParsedEnchantDefinition.invalid(parsedFields.errorMessage());
        }

        List<String> fields = parsedFields.fields();
        if (fields.size() < 2) {
            return ParsedEnchantDefinition.invalid("Missing display name or supported item targets.");
        }

        int targetStart = findEnchantTargetStart(fields);
        if (targetStart <= 0 || targetStart >= fields.size()) {
            return ParsedEnchantDefinition.invalid("Could not find supported item targets. Use targets like {sword, axe}, {armor}, or {tools}.");
        }

        String displayName = String.join(" ", fields.subList(0, targetStart)).trim();
        List<String> itemTokens = splitEnchantTargetFields(fields.subList(targetStart, fields.size()));
        if (displayName.isBlank()) {
            return ParsedEnchantDefinition.invalid("Display name cannot be blank.");
        }
        if (itemTokens.isEmpty()) {
            return ParsedEnchantDefinition.invalid("At least one supported item target is required.");
        }

        return ParsedEnchantDefinition.valid(namespacedEnchantKey, displayName, itemTokens);
    }

    private ParsedCommandFields parseGroupedCommandFields(String[] args, int startInclusive) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean grouped = false;
        char closingGroup = '\0';

        String raw = context.joinArgs(args, startInclusive, args.length);
        for (int i = 0; i < raw.length(); i++) {
            char character = raw.charAt(i);
            if (grouped) {
                if (character == closingGroup) {
                    addCommandField(fields, current);
                    grouped = false;
                    closingGroup = '\0';
                    continue;
                }
                current.append(character);
                continue;
            }

            if (Character.isWhitespace(character)) {
                addCommandField(fields, current);
                continue;
            }

            if ((character == '{' || character == '"' || character == '\'') && current.isEmpty()) {
                grouped = true;
                closingGroup = character == '{' ? '}' : character;
                continue;
            }

            current.append(character);
        }

        if (grouped) {
            return ParsedCommandFields.invalid("Missing closing " + closingGroup + " in enchant definition command.");
        }

        addCommandField(fields, current);
        return ParsedCommandFields.valid(fields);
    }

    private void addCommandField(List<String> fields, StringBuilder current) {
        String value = current.toString().trim();
        if (!value.isBlank()) {
            fields.add(value);
        }
        current.setLength(0);
    }

    private int findEnchantTargetStart(List<String> fields) {
        int targetStart = fields.size();
        while (targetStart > 0 && isEnchantTargetField(fields.get(targetStart - 1))) {
            targetStart--;
        }
        return targetStart;
    }

    private boolean isEnchantTargetField(String field) {
        List<String> tokens = splitEnchantTargetField(field);
        return !tokens.isEmpty() && tokens.stream().allMatch(this::isEnchantTargetAlias);
    }

    private List<String> splitEnchantTargetFields(List<String> fields) {
        List<String> result = new ArrayList<>();
        for (String field : fields) {
            result.addAll(splitEnchantTargetField(field));
        }
        return result.stream()
                .map(this::normalizeEnchantTargetToken)
                .filter(token -> !token.isBlank())
                .distinct()
                .toList();
    }

    private List<String> splitEnchantTargetField(String field) {
        if (field == null || field.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String token : field.split("[,\\s]+")) {
            String normalized = normalizeEnchantTargetToken(token);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private boolean isEnchantTargetAlias(String token) {
        return ENCHANT_TARGET_ALIASES.contains(normalizeEnchantTargetToken(token));
    }

    private String normalizeEnchantTargetToken(String token) {
        return token == null ? "" : token.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private record ParsedCommandFields(boolean valid, List<String> fields, String errorMessage) {
        private static ParsedCommandFields valid(List<String> fields) {
            return new ParsedCommandFields(true, fields, "");
        }

        private static ParsedCommandFields invalid(String errorMessage) {
            return new ParsedCommandFields(false, List.of(), errorMessage);
        }
    }

    private record ParsedEnchantDefinition(boolean valid, String namespacedEnchantKey, String displayName, List<String> itemTokens, String errorMessage) {
        private static ParsedEnchantDefinition valid(String namespacedEnchantKey, String displayName, List<String> itemTokens) {
            return new ParsedEnchantDefinition(true, namespacedEnchantKey, displayName, itemTokens, "");
        }

        private static ParsedEnchantDefinition invalid(String errorMessage) {
            return new ParsedEnchantDefinition(false, "", "", List.of(), errorMessage);
        }
    }
}
