package divinejason.divinemarketplace.auction.registry.custom;


/*
 * File role: Defines the service contract for custom item data source so command, GUI, and runtime code share one behavior boundary.
 */
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
