package com.cookiecraftmods.roadbuilder.preset;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.cookiecraftmods.roadbuilder.persistence.RoadPresetSavedData;
import net.minecraft.server.MinecraftServer;
import com.cookiecraftmods.roadbuilder.networking.RoadBuilderServerNetworking;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RoadPresetResources {
    private static final RoadPresetRepository REPOSITORY = new RoadPresetRepository();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static volatile MinecraftServer activeServer;

    private RoadPresetResources() {
    }

    public static RoadPresetRepository repository() {
        return REPOSITORY;
    }

        public static void registerReloadListener() {
        if (!REGISTERED.compareAndSet(false, true)) {
            throw new IllegalStateException("road preset reload listener is already registered");
        }
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new RoadPresetReloadListener(REPOSITORY));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            activeServer = server;
            RoadPresetSavedData.get(server.overworld())
                    .loadInto(REPOSITORY);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            activeServer = null;
            REPOSITORY.replaceServerAuthored(java.util.Map.of());
        });
    }

    static void catalogReloaded() {
        MinecraftServer server = activeServer;
        if (server != null) {
            RoadBuilderServerNetworking.broadcastPresetCatalog(server);
        }
    }
}
