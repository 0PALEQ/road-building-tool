package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.placement.ConstructionJob;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobBlockTransition;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

public final class RoadBuilderMutationKillFixture {
    private RoadBuilderMutationKillFixture() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "Expected <prepare|verify> <boundary> <directory>"
            );
        }
        MinecraftBootstrap.bootstrap();
        String mode = arguments[0];
        String boundary = arguments[1];
        Path directory = Path.of(arguments[2]).toAbsolutePath().normalize();
        Path journal = directory.resolve(boundary + ".journal.dat");
        if ("prepare".equals(mode)) {
            Files.createDirectories(directory);
            RoadBuilderConstructionJobSavedData data =
                    RoadBuilderConstructionJobSavedData.create(Level.OVERWORLD);
            data.add(jobAt(boundary));
            CompoundTag envelope = new CompoundTag();
            envelope.put("data", data.save(new CompoundTag()));
            Path temporary = directory.resolve(boundary + ".tmp");
            RoadNbtIo.writeCompressed(envelope, temporary);
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE
            )) {
                channel.force(true);
            }
            Files.move(
                    temporary,
                    journal,
                    StandardCopyOption.REPLACE_EXISTING
            );
            Runtime.getRuntime().halt(137);
            return;
        }
        if (!"verify".equals(mode)) {
            throw new IllegalArgumentException("unknown mode " + mode);
        }
        CompoundTag envelope = RoadNbtIo.readCompressed(journal);
        RoadBuilderConstructionJobSavedData loaded =
                RoadBuilderConstructionJobSavedData.load(
                        Level.OVERWORLD,
                        envelope.getCompound("data")
                );
        if (!loaded.isCompatible() || loaded.jobs().size() != 1) {
            throw new IllegalStateException(
                    "crash journal did not survive " + boundary
            );
        }
        ConstructionJob reconciled = loaded.jobs().get(0)
                .restartForWorldReconciliation();
        if (reconciled.cursor() != 0
                || reconciled.transitions().size() != 3) {
            throw new IllegalStateException(
                    "recovery trusted an advisory cursor at " + boundary
            );
        }
    }

    private static ConstructionJob jobAt(String boundary) {
        ConstructionJob job = ConstructionJob.prepared(
                new UUID(0, 100),
                new UUID(0, 200),
                ConstructionJobAction.PLACE,
                List.of(
                        transition(0),
                        transition(1),
                        transition(2)
                )
        );
        return switch (boundary) {
            case "prepared_flush" -> job;
            case "applying_flush" -> job.start();
            case "world_batch_flush" -> job.start().advance(2);
            case "terminal_checkpoint" -> job.start().advance(3);
            default -> throw new IllegalArgumentException(
                    "unknown boundary " + boundary
            );
        };
    }

    private static ConstructionJobBlockTransition transition(int x) {
        return new ConstructionJobBlockTransition(
                new BlockPos(x, 64, 0),
                Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState()
        );
    }
}
