package com.wish.client.util;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class WishPaths {

    private WishPaths() {}

    public static Path resolveBaseConfigDir() {
        try {
            return FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            return Path.of("config").toAbsolutePath().normalize();
        }
    }

    public static Path getConfigDir() {
        return resolveBaseConfigDir().resolve("wish").normalize();
    }

    public static Path getLegacyConfigFile(String fileName) {
        return resolveContained(resolveBaseConfigDir(), fileName);
    }

    public static Path getGameDir() {
        try {
            return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            return Path.of("").toAbsolutePath().normalize();
        }
    }

    public static Path resolveContained(Path baseDir, String relativePath) {
        if (baseDir == null || relativePath == null) {
            throw new IllegalArgumentException("Base directory and relative path must not be null");
        }
        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("Path escapes base directory: " + relativePath);
        }
        return resolved;
    }

    public static void writeStringAtomically(Path target, String content) throws IOException {
        writeBytesAtomically(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeBytesAtomically(Path target, byte[] bytes) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            Files.write(tempFile, bytes);
            try {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
