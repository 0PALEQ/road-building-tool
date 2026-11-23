package com.cookiecraftmods.roadbuilder.preset;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RoadPresetReloadListener extends SimpleJsonResourceReloadListener
        implements IdentifiableResourceReloadListener {
    public static final String DIRECTORY = "road_presets";
    public static final ResourceLocation RELOAD_ID =
            MinecraftIds.id("roadbuilder", "road_presets");

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("roadbuilder/presets");

    private final java.util.function.Consumer<Map<ResourceLocation, RoadPreset>> receiver;

    public RoadPresetReloadListener(RoadPresetRepository repository) {
        this(presets -> {
            repository.replaceAll(presets);
            RoadPresetResources.catalogReloaded();
        });
    }

    public RoadPresetReloadListener(
            java.util.function.Consumer<Map<ResourceLocation, RoadPreset>> receiver
    ) {
        super(GSON, DIRECTORY);
        this.receiver = Objects.requireNonNull(receiver, "receiver");
    }

    @Override
    public ResourceLocation getFabricId() {
        return RELOAD_ID;
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        Map<ResourceLocation, RoadPreset> decoded = new LinkedHashMap<>();
        resources.forEach((id, json) -> {
            DataResult<RoadPreset> parsed = RoadPreset.CODEC.parse(JsonOps.INSTANCE, json);
            if (parsed.error().isPresent()) {
                LOGGER.error(
                        "Could not load road preset {}: {}",
                        id,
                        parsed.error().orElseThrow().message()
                );
                return;
            }
            parsed.result()
                    .filter(preset -> validateSurfaceBlock(id, preset))
                    .ifPresent(preset -> decoded.put(id, preset));
        });

        receiver.accept(Map.copyOf(decoded));
        LOGGER.info("Loaded {} road preset(s)", decoded.size());
    }

    public static boolean validateSurfaceBlock(ResourceLocation id, RoadPreset preset) {
        HashSet<ResourceLocation> blockIds =
                new HashSet<>(preset.surfaceColumnBlocks());
        blockIds.add(preset.surfaceBlock());
        preset.surfaceColumns().forEach(column -> {
            blockIds.addAll(column.blocks());
            blockIds.addAll(column.underlayBlocks());
        });
        blockIds.add(preset.materialPalette().pavementBlock());
        blockIds.add(preset.materialPalette().markingBlock());
        blockIds.add(preset.materialPalette().sidewalkBlock());
        blockIds.add(preset.materialPalette().grassMedianBlock());
        blockIds.add(preset.materialPalette().concreteMedianBlock());
        java.util.Arrays.stream(RoadSurfaceRole.values()).forEach(role ->
                blockIds.addAll(preset.materialPalette().blockSetFor(role)));
        blockIds.add(preset.intersectionStyle().pavementBlock());
        preset.intersectionStyle().markingBlock().ifPresent(blockIds::add);
        preset.intersectionStyle().crosswalkBlock().ifPresent(blockIds::add);
        preset.intersectionStyle().stopLineBlock().ifPresent(blockIds::add);
        for (ResourceLocation blockId : blockIds) {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(blockId);
            if (block.isEmpty()) {
                LOGGER.error(
                        "Could not load road preset {}: unknown surface block {}",
                        id,
                        blockId
                );
                return false;
            }
            if (block.orElseThrow().defaultBlockState().isAir()) {
                LOGGER.error(
                        "Could not load road preset {}: surface block {} cannot be air",
                        id,
                        blockId
                );
                return false;
            }
        }
        return true;
    }
}
