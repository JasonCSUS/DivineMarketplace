package divinejason.divinemarketplace.storage.sqlite;

/*
 * Layer : storage/sqlite/core
 * Owns  : a group of SQLite mutations that must flush as one transaction
 * Calls : SQLiteMutation only
 * Avoids: service logic, Bukkit API, and connection management
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Transaction-sized set of SQLite key/value mutations.
 *
 * <p>The write-behind queue should eventually receive these from services after
 * memory has already been updated.  A purchase, claim, cancel, or price recalc
 * should enqueue one batch instead of letting multiple stores write separately.</p>
 */
public record SQLiteWriteBatch(String reason, List<SQLiteMutation> mutations) {
    public SQLiteWriteBatch {
        reason = reason == null || reason.isBlank() ? "unspecified" : reason.trim();
        mutations = List.copyOf(Objects.requireNonNull(mutations, "mutations"));
    }

    public static Builder builder(String reason) {
        return new Builder(reason);
    }

    public static SQLiteWriteBatch of(String reason, Collection<SQLiteMutation> mutations) {
        return new SQLiteWriteBatch(reason, List.copyOf(Objects.requireNonNull(mutations, "mutations")));
    }

    public boolean isEmpty() {
        return mutations.isEmpty();
    }

    public static final class Builder {
        private final String reason;
        private final List<SQLiteMutation> mutations = new ArrayList<>();

        private Builder(String reason) {
            this.reason = reason;
        }

        public Builder put(String tableName, String id, String value) {
            mutations.add(SQLiteMutation.put(tableName, id, value));
            return this;
        }

        public Builder delete(String tableName, String id) {
            mutations.add(SQLiteMutation.delete(tableName, id));
            return this;
        }

        public Builder add(SQLiteMutation mutation) {
            mutations.add(Objects.requireNonNull(mutation, "mutation"));
            return this;
        }

        public SQLiteWriteBatch build() {
            return new SQLiteWriteBatch(reason, mutations);
        }
    }
}
