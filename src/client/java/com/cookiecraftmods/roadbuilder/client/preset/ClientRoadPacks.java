package com.cookiecraftmods.roadbuilder.client.preset;

import com.cookiecraftmods.roadbuilder.client.networking.RoadBuilderClientNetworking;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import java.util.Map;

/** Enabled resource packs supply layouts only to this client. */
public final class ClientRoadPacks {
    private static volatile Map<ResourceLocation, RoadPreset> presets = Map.of();
    private ClientRoadPacks() { }
    public static Map<ResourceLocation, RoadPreset> presets() { return presets; }
    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new RoadPresetReloadListener(loaded -> Minecraft.getInstance().execute(() -> {
                    presets = loaded;
                    RoadBuilderClientNetworking.refreshClientRoadPacks();
                })));
    }
}
