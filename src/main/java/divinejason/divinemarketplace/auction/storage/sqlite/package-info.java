/**
 * SQLite-backed market stores.
 *
 * <p>Stores load state into memory first. Runtime services should read from these
 * memory/index views and let each store handle durability, reload, and payload-size
 * accounting.
 */
package divinejason.divinemarketplace.auction.storage.sqlite;
