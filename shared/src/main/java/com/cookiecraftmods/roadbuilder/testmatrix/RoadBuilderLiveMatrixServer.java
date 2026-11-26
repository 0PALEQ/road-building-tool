package com.cookiecraftmods.roadbuilder.testmatrix;

import com.cookiecraftmods.roadbuilder.RoadBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

public final class RoadBuilderLiveMatrixServer {
    private static final String ENABLED_PROPERTY =
            "roadbuilder.matrix.enabled";
    private static final int TIMEOUT_TICKS = 20 * 600;
    private static boolean registered;
    private static int startedAtTick;
    private static int completionObservedAt = -1;
    private static boolean sentToNether;
    private static boolean returnedFromNether;
    private static boolean initialized;

    private RoadBuilderLiveMatrixServer() {
    }

    public static synchronized void registerIfEnabled() {
        if (registered || !Boolean.getBoolean(ENABLED_PROPERTY)) {
            return;
        }
        if (!"server".equals(System.getProperty(
                "roadbuilder.matrix.role",
                ""
        ))) {
            return;
        }

        ServerLifecycleEvents.SERVER_STARTED.register(
                RoadBuilderLiveMatrixServer::serverStarted
        );
        ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> writeEvidence("server-stopped", "ok")
        );
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) ->
                        server.execute(() -> configurePlayer(handler.player))
        );
        ServerTickEvents.END_SERVER_TICK.register(
                RoadBuilderLiveMatrixServer::tick
        );
        registered = true;
    }

    private static void serverStarted(MinecraftServer server) {
        startedAtTick = server.getTickCount();
        completionObservedAt = -1;
        sentToNether = false;
        returnedFromNether = false;
        initialized = false;
        initialize(server);
    }

    private static void initialize(MinecraftServer server) {
        try {
            if ("place".equals(phase())) {
                prepareUnevenTerrain(server.overworld());
            }
            prepareNetherArrival(server.getLevel(Level.NETHER));
            MatrixServerPlayer.setSpawn(server.overworld(), new BlockPos(40, 76, 36));
            writeEvidence("server-ready", Instant.now().toString());
            initialized = true;
            RoadBuilder.LOGGER.info(
                    "Road Builder live matrix server is ready for {} {}",
                    phase(),
                    System.getProperty(
                            "roadbuilder.matrix.handedness",
                            "RHT"
                    )
            );
        } catch (RuntimeException exception) {
            fail(server, "server setup failed: " + exception);
        }
    }

    private static void configurePlayer(ServerPlayer player) {
        String name = MatrixServerPlayer.name(player);
        if ("RoadActor".equals(name)) {
            player.setGameMode(GameType.CREATIVE);
        } else if ("RoadObserver".equals(name)) {
            player.setGameMode(GameType.SURVIVAL);
        }
        MatrixServerPlayer.teleport(player, player.serverLevel().getServer().overworld(), 40.5, 76.0, 36.5);
    }

    private static void tick(MinecraftServer server) {
        if (!initialized) {
            startedAtTick = server.getTickCount();
            initialize(server);
            return;
        }
        if (exists("failure-actor") || exists("failure-observer")) {
            fail(server, "a live client reported failure");
            return;
        }
        if (server.getTickCount() - startedAtTick > TIMEOUT_TICKS) {
            fail(server, "matrix server timed out in phase " + phase());
            return;
        }

        if ("reload".equals(phase())) {
            driveDimensionRoundTrip(server);
        }

        if (!exists("actor-complete") || !exists("observer-complete")) {
            completionObservedAt = -1;
            return;
        }
        if (completionObservedAt < 0) {
            completionObservedAt = server.getTickCount();
            writeEvidence("server-completing", "ok");
            server.saveEverything(true, true, true);
            return;
        }
        if (server.getTickCount() - completionObservedAt >= 40) {
            writeEvidence("server-complete", "ok");
            server.halt(false);
        }
    }

    private static void driveDimensionRoundTrip(MinecraftServer server) {
        ServerPlayer actor = server.getPlayerList().getPlayerByName(
                "RoadActor"
        );
        if (actor == null) {
            return;
        }
        if (!sentToNether && exists("dimension-request")) {
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (nether == null) {
                fail(server, "Nether level was not available");
                return;
            }
            MatrixServerPlayer.teleport(actor, nether, 0.5, 80.0, 0.5);
            sentToNether = true;
            writeEvidence("server-sent-nether", "ok");
            return;
        }
        if (sentToNether
                && !returnedFromNether
                && exists("dimension-nether-observed")) {
            MatrixServerPlayer.teleport(actor, server.overworld(), 40.5, 76.0, 36.5);
            returnedFromNether = true;
            writeEvidence("server-returned-overworld", "ok");
        }
    }

    private static void prepareUnevenTerrain(ServerLevel level) {
        Objects.requireNonNull(level, "overworld");
        for (int x = 0; x <= 100; x++) {
            int height = 64 + Math.max(0, Math.min(3, (x - 4) / 24));
            for (int centerZ : new int[]{0, 18, 36, 54, 72}) {
                for (int z = centerZ - 7; z <= centerZ + 7; z++) {
                    level.setBlock(
                            new BlockPos(x, height, z),
                            Blocks.STONE.defaultBlockState(),
                            2
                    );
                }
            }
        }
    }

    private static void prepareNetherArrival(ServerLevel nether) {
        if (nether == null) {
            return;
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                nether.setBlock(
                        new BlockPos(x, 79, z),
                        Blocks.STONE.defaultBlockState(),
                        2
                );
                for (int y = 80; y <= 84; y++) {
                    nether.setBlock(
                            new BlockPos(x, y, z),
                            Blocks.AIR.defaultBlockState(),
                            2
                    );
                }
            }
        }
    }

    private static void fail(MinecraftServer server, String detail) {
        RoadBuilder.LOGGER.error("Road Builder live matrix: {}", detail);
        writeEvidence("failure", detail);
        server.halt(false);
    }

    private static boolean exists(String name) {
        return Files.isRegularFile(evidencePath(name));
    }

    private static void writeEvidence(String name, String value) {
        Path path = evidencePath(name);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    value + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException exception) {
            RoadBuilder.LOGGER.error(
                    "Could not write live matrix evidence {}",
                    path,
                    exception
            );
        }
    }

    private static Path evidencePath(String name) {
        String evidenceDirectory = System.getProperty(
                "roadbuilder.matrix.evidenceDir",
                ""
        );
        if (evidenceDirectory.isBlank()) {
            throw new IllegalStateException(
                    "roadbuilder.matrix.evidenceDir is required"
            );
        }
        return Path.of(evidenceDirectory)
                .resolve(phase() + "-" + name + ".txt");
    }

    private static String phase() {
        return System.getProperty("roadbuilder.matrix.phase", "place");
    }
}
