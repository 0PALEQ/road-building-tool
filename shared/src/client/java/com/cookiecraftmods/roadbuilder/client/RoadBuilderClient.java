package com.cookiecraftmods.roadbuilder.client;

import com.cookiecraftmods.roadbuilder.client.config.RoadBuilderClientPreferences;
import com.cookiecraftmods.roadbuilder.client.config.RoadBuilderClientPreferencesLoader;
import com.cookiecraftmods.roadbuilder.client.input.RoadBuilderKeyBindings;
import com.cookiecraftmods.roadbuilder.client.mode.RoadBuilderModeController;
import com.cookiecraftmods.roadbuilder.client.networking.RoadBuilderClientNetworking;
import com.cookiecraftmods.roadbuilder.client.render.RoadLaneDirectionRenderer;
import com.cookiecraftmods.roadbuilder.client.render.RoadPreviewMeasurementsRenderer;
import com.cookiecraftmods.roadbuilder.client.render.StraightRoadPreviewRenderer;
import com.cookiecraftmods.roadbuilder.client.testmatrix.RoadBuilderLiveMatrixClient;
import com.cookiecraftmods.roadbuilder.client.tool.RoadBuilderTool;
import com.cookiecraftmods.roadbuilder.client.ui.RoadBuilderToolbarRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public final class RoadBuilderClient implements ClientModInitializer {
    private static final RoadBuilderClientPreferences PREFERENCES =
            RoadBuilderClientPreferencesLoader.load();
    private static final RoadBuilderModeController MODE_CONTROLLER =
            new RoadBuilderModeController(PREFERENCES);
    private static final RoadBuilderToolbarRenderer TOOLBAR_RENDERER = new RoadBuilderToolbarRenderer();
    private static final RoadPreviewMeasurementsRenderer PREVIEW_MEASUREMENTS = new RoadPreviewMeasurementsRenderer();
    private static final StraightRoadPreviewRenderer PREVIEW_RENDERER = new StraightRoadPreviewRenderer();
    private static final RoadLaneDirectionRenderer LANE_DIRECTION_RENDERER =
            new RoadLaneDirectionRenderer(PREFERENCES.guidelineRange());

    @Override
    public void onInitializeClient() {
        RoadBuilderClientNetworking.register();
        com.cookiecraftmods.roadbuilder.client.preset.ClientRoadPacks.register();
        RoadBuilderKeyBindings.register();
        RoadBuilderLiveMatrixClient.registerIfEnabled();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            RoadBuilderClientNetworking.tick(client);
            while (RoadBuilderKeyBindings.consumeToggleMode()) {
                MODE_CONTROLLER.toggle(client);
            }

            MODE_CONTROLLER.tick(client);
            RoadBuilderLiveMatrixClient.tickIfEnabled(client);
        });
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            if (MODE_CONTROLLER.isActive()) {
                PREVIEW_MEASUREMENTS.renderOverlay(graphics);
                TOOLBAR_RENDERER.render(
                        graphics,
                        Minecraft.getInstance(),
                        MODE_CONTROLLER.straightRoadTool()
                );
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            var tool = MODE_CONTROLLER.straightRoadTool();
            if (MODE_CONTROLLER.isActive()) {
                tool.updatePointer(Minecraft.getInstance());
            }
            if (MODE_CONTROLLER.isActive()
                    && tool.activeTool() == RoadBuilderTool.LANE_CONNECTOR) {
                LANE_DIRECTION_RENDERER.render(
                        context,
                        Minecraft.getInstance(),
                        tool.selectedLaneConnectionPoint(),
                        tool.hoveredLaneConnectionPoint()
                );
            } else if (tool.lanePathsVisible()) {
                LANE_DIRECTION_RENDERER.render(
                        context,
                        Minecraft.getInstance()
                );
            }
            if (MODE_CONTROLLER.isActive()) {
                PREVIEW_RENDERER.render(context, tool);
                PREVIEW_MEASUREMENTS.capture(context, tool);
            }
        });
    }

    public static RoadBuilderModeController modeController() {
        return MODE_CONTROLLER;
    }
}
