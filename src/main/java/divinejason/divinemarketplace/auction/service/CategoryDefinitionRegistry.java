package divinejason.divinemarketplace.auction.service;


/*
 * File role: Defines the service contract for category definition registry so command, GUI, and runtime code share one behavior boundary.
 */
import java.util.List;

import divinejason.divinemarketplace.auction.model.CategoryDefinition;

/**
 * Registry for config-defined top-level categories.
 *
 * Notes:
 * - categories should preserve config order
 * - top-level categories are always shown in the UI
 * - menu slot placement is dynamic and handled elsewhere
 */
public interface CategoryDefinitionRegistry {
    List<CategoryDefinition> getAllCategoriesInDisplayOrder();
}
