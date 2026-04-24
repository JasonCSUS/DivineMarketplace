package divinejason.divinemarketplace.config;

/**
 * Typed runtime representation of config.yml only.
 *
 * This does not cover category definitions, category item mappings,
 * custom item definitions, or custom enchant definitions.
 * Those belong to their own registries/data sources.
 *
 * MainConfig should hold only global plugin behavior/settings such as:
 * - listing duration and listing policy tiers
 * - item claim abandon duration
 * - package preview mode
 * - storage backend choices
 * - binary file size limits / cleanup thresholds
 * - market recalculation intervals / thresholds
 * - alert behavior
 *
 * Money rule:
 * - all internal money values are stored as integer hundredths
 * - convert to decimal only at the display/Vault boundary
 *
 * NOTE:
 * - Other classes should usually access these values through ConfigService's
 *   specific helper getters rather than by drilling through the full config tree.
 * - MainConfig acts like the parsed source object; ConfigService acts like the
 *   central singleton access point.
 */
public final class MainConfig {
}
