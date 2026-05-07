package divinejason.divinemarketplace.storage.sqlite;

/*
 * Layer : storage/sqlite/core
 * Owns  : one logical key/value table mutation for a future write batch
 * Calls : no other project layer
 * Avoids: service rules, Bukkit API, and direct execution
 */

import java.util.Objects;

/** A single namespaced SQLite key/value mutation. */
public record SQLiteMutation(Operation operation, String tableName, String id, String value) {
    public enum Operation {
        PUT,
        DELETE
    }

    public SQLiteMutation {
        Objects.requireNonNull(operation, "operation");
        tableName = requireNonBlank(tableName, "tableName");
        id = requireNonBlank(id, "id");
        if (operation == Operation.PUT) {
            value = requireNonBlank(value, "value");
        } else {
            value = null;
        }
    }

    public static SQLiteMutation put(String tableName, String id, String value) {
        return new SQLiteMutation(Operation.PUT, tableName, id, value);
    }

    public static SQLiteMutation delete(String tableName, String id) {
        return new SQLiteMutation(Operation.DELETE, tableName, id, null);
    }

    private static String requireNonBlank(String input, String field) {
        Objects.requireNonNull(input, field);
        if (input.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return input;
    }
}
