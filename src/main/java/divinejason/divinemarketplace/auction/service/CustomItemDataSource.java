package divinejason.divinemarketplace.auction.service;

import java.util.Map;

import divinejason.divinemarketplace.auction.model.CustomItemDefinition;

/**
 * Loads configured custom item definitions from the selected backend.
 *
 * The data source reads from YAML/CSV/SQL. The registry owns the in-memory cache.
 */
public interface CustomItemDataSource {
    Map<String, CustomItemDefinition> loadDefinitions();
}
