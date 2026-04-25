package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CustomItemDefinition;

import java.util.Map;

/**
 * Loads and persists configured custom item definitions from the selected backend.
 *
 * The data source owns file/backend I/O.
 * The registry owns the in-memory cache and match indexes.
 */
public interface CustomItemDataSource {
    Map<String, CustomItemDefinition> loadDefinitions();

    CustomItemDefinition upsertDefinition(CustomItemDefinition definition);
}
