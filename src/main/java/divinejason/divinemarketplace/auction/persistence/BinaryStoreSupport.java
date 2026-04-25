package divinejason.divinemarketplace.auction.persistence;

import org.bukkit.inventory.ItemStack;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared binary-format helpers for the store implementations.
 *
 * Paper usage:
 * - ItemStack snapshots are serialized with Paper's raw byte helpers rather than
 *   Bukkit object streams.
 */
final class BinaryStoreSupport {
    private BinaryStoreSupport() {
    }

    static void ensureFileExists(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
    }

    static DataInputStream newInput(Path path) throws IOException {
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(path)));
    }

    static DataOutputStream newOutput(Path path) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
    }

    static boolean isEmptyFile(Path path) throws IOException {
        return !Files.exists(path) || Files.size(path) == 0L;
    }

    static Path writeToTempFile(Path target, Consumer<DataOutputStream> writer) throws IOException {
        ensureFileExists(target);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");

        try (DataOutputStream output = newOutput(temp)) {
            writer.accept(output);
            output.flush();
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    static void writeHeader(DataOutputStream output, String magic, int version) throws IOException {
        output.writeUTF(magic);
        output.writeInt(version);
    }

    static void requireHeader(DataInputStream input, String expectedMagic, int expectedVersion) throws IOException {
        String magic = input.readUTF();
        int version = input.readInt();

        if (!expectedMagic.equals(magic)) {
            throw new IOException("Unexpected binary file magic. Expected " + expectedMagic + " but found " + magic);
        }

        if (expectedVersion != version) {
            throw new IOException("Unsupported binary file version. Expected " + expectedVersion + " but found " + version);
        }
    }

    static void writeString(DataOutputStream output, String value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeUTF(value);
        }
    }

    static String readString(DataInputStream input) throws IOException {
        return input.readBoolean() ? input.readUTF() : null;
    }

    static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeLong(value.getMostSignificantBits());
            output.writeLong(value.getLeastSignificantBits());
        }
    }

    static UUID readUuid(DataInputStream input) throws IOException {
        if (!input.readBoolean()) {
            return null;
        }
        long most = input.readLong();
        long least = input.readLong();
        return new UUID(most, least);
    }

    static void writeBytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }

    static byte[] readBytes(DataInputStream input) throws IOException {
        int length = input.readInt();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    static void writeItemStack(DataOutputStream output, ItemStack itemStack) throws IOException {
        byte[] bytes = itemStack.serializeAsBytes();
        writeBytes(output, bytes);
    }

    static ItemStack readItemStack(DataInputStream input) throws IOException {
        return ItemStack.deserializeBytes(readBytes(input));
    }

    static <T> List<T> page(List<T> source, int page, int pageSize) {
        if (source.isEmpty() || pageSize <= 0 || page < 0) {
            return Collections.emptyList();
        }

        int fromIndex = page * pageSize;
        if (fromIndex >= source.size()) {
            return Collections.emptyList();
        }

        int toIndex = Math.min(source.size(), fromIndex + pageSize);
        return new ArrayList<>(source.subList(fromIndex, toIndex));
    }
}
