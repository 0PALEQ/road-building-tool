package com.cookiecraftmods.roadbuilder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public final class JsonConfigIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonConfigIO() {
    }

    public static JsonObject readObject(Path path) throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("root must be a JSON object");
        }
        return root.getAsJsonObject();
    }

    public static void requireExactKeys(JsonObject root, Set<String> expectedKeys) {
        Set<String> actualKeys = root.keySet();
        if (!actualKeys.equals(expectedKeys)) {
            throw new IllegalArgumentException(
                    "expected exactly " + expectedKeys + " but found " + actualKeys
            );
        }
    }

    public static int integer(JsonObject root, String key) {
        double value = number(root, key);
        if (value != Math.rint(value)
                || value < Integer.MIN_VALUE
                || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return (int) value;
    }

    public static double number(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        double parsed = value.getAsDouble();
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(key + " must be finite");
        }
        return parsed;
    }

    public static boolean bool(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be boolean");
        }
        return value.getAsBoolean();
    }

    public static <T extends Enum<T>> T enumValue(
            JsonObject root,
            String key,
            Class<T> type
    ) {
        JsonElement value = root.get(key);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        try {
            return Enum.valueOf(type, value.getAsString());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    key + " has unknown value " + value.getAsString(),
                    invalid
            );
        }
    }

    public static void writeAtomic(Path path, String temporaryFilePrefix, JsonObject root)
            throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("configuration path has no parent: " + path);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, temporaryFilePrefix, ".tmp");
        try {
            Files.writeString(
                    temporary,
                    GSON.toJson(root) + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
