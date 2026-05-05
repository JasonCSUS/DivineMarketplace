package divinejason.divinemarketplace.auction.persistence.sqlite;


/*
 * File role: Serializes and deserializes ItemStack snapshots and UUID/time values shared by SQLite persistence stores.
 */
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;
import java.util.UUID;

/**
 * Small record serialization helper for SQLite-backed stores.
 */
public final class SQLiteRecordCodecSupport {
    private SQLiteRecordCodecSupport() {
    }

    public static String encode(Encoder encoder) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(byteStream)) {
                encoder.encode(output);
            }
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode SQLite blob payload.", exception);
        }
    }

    public static <T> T decode(String value, Decoder<T> decoder) {
        try {
            byte[] raw = Base64.getDecoder().decode(value);
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
                return decoder.decode(input);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode SQLite blob payload.", exception);
        }
    }

    public static void writeString(DataOutputStream output, String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeUTF(value);
        }
    }

    public static String readString(DataInputStream input) throws IOException {
        return input.readBoolean() ? input.readUTF() : null;
    }

    public static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        writeString(output, value == null ? null : value.toString());
    }

    public static UUID readUuid(DataInputStream input) throws IOException {
        String value = readString(input);
        return value == null ? null : UUID.fromString(value);
    }

    public static void writeItemStack(DataOutputStream output, ItemStack itemStack) throws IOException {
        output.writeBoolean(itemStack != null);
        if (itemStack == null) {
            return;
        }

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream objectOutput = new BukkitObjectOutputStream(byteStream)) {
            objectOutput.writeObject(itemStack);
        }

        byte[] itemBytes = byteStream.toByteArray();
        output.writeInt(itemBytes.length);
        output.write(itemBytes);
    }

    public static ItemStack readItemStack(DataInputStream input) throws IOException {
        if (!input.readBoolean()) {
            return null;
        }

        int length = input.readInt();
        byte[] itemBytes = input.readNBytes(length);

        try (BukkitObjectInputStream objectInput = new BukkitObjectInputStream(new ByteArrayInputStream(itemBytes))) {
            Object object = objectInput.readObject();
            return object instanceof ItemStack itemStack ? itemStack : null;
        } catch (ClassNotFoundException exception) {
            throw new IOException("Failed to deserialize ItemStack class.", exception);
        }
    }

    @FunctionalInterface
    public interface Encoder {
        void encode(DataOutputStream output) throws IOException;
    }

    @FunctionalInterface
    public interface Decoder<T> {
        T decode(DataInputStream input) throws IOException;
    }
}
