package com.cookiecraftmods.roadbuilder.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class DurableMutationJournal {
    public static final String FILE_NAME =
            "roadbuilder_construction_jobs.journal.dat";

    private DurableMutationJournal() {
    }

    public static void flush(
            ServerLevel level,
            RoadBuilderConstructionJobSavedData jobs
    ) throws IOException {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(jobs, "jobs");
        Path target = path(level);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(
                target.getParent(),
                FILE_NAME,
                ".tmp"
        );
        try {
            CompoundTag envelope = new CompoundTag();
            envelope.put("data", jobs.save(new CompoundTag()));
            NbtIo.writeCompressed(envelope, temporary.toFile());
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE
            )) {
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            forceDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static boolean recover(
            ServerLevel level,
            RoadBuilderConstructionJobSavedData jobs
    ) throws IOException {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(jobs, "jobs");
        Path path = path(level);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        CompoundTag envelope = NbtIo.readCompressed(path.toFile());
        if (!envelope.contains("data", CompoundTag.TAG_COMPOUND)) {
            throw new IOException("mutation journal has no data envelope");
        }
        RoadBuilderConstructionJobSavedData recovered =
                RoadBuilderConstructionJobSavedData.load(
                        level.dimension(),
                        envelope.getCompound("data")
                );
        if (!recovered.isCompatible()) {
            throw new IOException(
                    "mutation journal is incompatible: "
                            + recovered.incompatibilityReason().orElse(
                                    "unknown reason"
                            )
            );
        }
        jobs.replaceFromDurableJournal(recovered.jobs());
        return true;
    }

    public static Path path(ServerLevel level) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimension = DimensionType.getStorageFolder(
                level.dimension(),
                root
        );
        return dimension.resolve("data").resolve(FILE_NAME);
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(
                directory,
                StandardOpenOption.READ
        )) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows and some providers do not allow opening directories.
        }
    }
}
