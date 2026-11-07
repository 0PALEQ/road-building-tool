package com.cookiecraftmods.roadbuilder.client.mode;

import com.cookiecraftmods.roadbuilder.client.camera.CityBuilderCameraController;
import com.cookiecraftmods.roadbuilder.client.camera.CityBuilderCameraPose;
import com.cookiecraftmods.roadbuilder.client.config.RoadBuilderClientPreferences;
import com.cookiecraftmods.roadbuilder.client.tool.StraightRoadToolController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.Optional;

public final class RoadBuilderModeController {
    private RoadBuilderModeState state = RoadBuilderModeState.INACTIVE;
    private ClientLevel entryLevel;
    private boolean restoreMouseGrab;
    private boolean pendingMouseRestore;
    private final StraightRoadToolController straightRoadTool;
    private final CityBuilderCameraController cameraController;

    public RoadBuilderModeController() {
        this(RoadBuilderClientPreferences.DEFAULTS);
    }

    public RoadBuilderModeController(RoadBuilderClientPreferences preferences) {
        RoadBuilderClientPreferences clientPreferences =
                Objects.requireNonNull(preferences, "preferences");
        straightRoadTool = new StraightRoadToolController(
                clientPreferences.snapSettings()
        );
        cameraController = new CityBuilderCameraController(
                clientPreferences
        );
    }

    public RoadBuilderModeState state() {
        return state;
    }

    public boolean isActive() {
        return state == RoadBuilderModeState.ACTIVE;
    }

    public StraightRoadToolController straightRoadTool() {
        return straightRoadTool;
    }

    public Optional<CityBuilderCameraPose> cameraPose(Entity focusEntity, float partialTick) {
        return cameraController.pose(focusEntity, partialTick);
    }

    public void toggle(Minecraft client) {
        Objects.requireNonNull(client, "client");

        if (isActive()) {
            exit(client, RoadBuilderModeExitReason.USER_TOGGLED);
            return;
        }

        tryEnter(client);
    }

    public void tick(Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (isActive()) {
            if (!client.isWindowActive()) {
                exit(client, RoadBuilderModeExitReason.FOCUS_LOST);
            } else if (client.player == null || client.level == null) {
                exit(client, RoadBuilderModeExitReason.DISCONNECTED);
            } else if (client.level != entryLevel) {
                exit(client, RoadBuilderModeExitReason.WORLD_CHANGED);
            } else if (!client.player.isCreative()) {
                client.player.displayClientMessage(
                        Component.translatable("message.roadbuilder.creative_permission_lost"),
                        true
                );
                exit(client, RoadBuilderModeExitReason.CREATIVE_PERMISSION_LOST);
            }
        }

        straightRoadTool.tick(client, isActive());
        if (!isActive()) {
            restoreMouseWhenReady(client);
            return;
        }

        drainConflictingActionClicks(client);
        cameraController.tick(client);

        // Vanilla may recapture the cursor after a screen or focus transition.
        if (client.mouseHandler.isMouseGrabbed()) {
            client.mouseHandler.releaseMouse();
        }
    }

    public void exit(Minecraft client, RoadBuilderModeExitReason reason) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(reason, "reason");

        if (!isActive()) {
            return;
        }

        straightRoadTool.onModeExited(reason);
        cameraController.exit(client);
        state = RoadBuilderModeState.INACTIVE;
        entryLevel = null;
        pendingMouseRestore |= restoreMouseGrab;
        restoreMouseGrab = false;
        restoreMouseWhenReady(client);
    }

    private void tryEnter(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        if (!client.player.isCreative()) {
            client.player.displayClientMessage(Component.translatable("message.roadbuilder.creative_only"), true);
            return;
        }

        entryLevel = client.level;
        restoreMouseGrab = pendingMouseRestore || client.mouseHandler.isMouseGrabbed();
        pendingMouseRestore = false;
        state = RoadBuilderModeState.ACTIVE;
        client.mouseHandler.releaseMouse();
        cameraController.enter(client);
        straightRoadTool.onModeEntered(client);
    }

    public boolean handleMouseButton(
            Minecraft client,
            long window,
            int button,
            int action
    ) {
        Objects.requireNonNull(client, "client");
        return isActive()
                && (cameraController.handleMouseButton(client, window, button, action)
                || straightRoadTool.handleMouseButton(client, window, button, action));
    }

    public void handleCursorPosition(
            Minecraft client,
            long window,
            double x,
            double y
    ) {
        Objects.requireNonNull(client, "client");
        if (isActive()) {
            cameraController.handleCursorPosition(client, window, x, y);
        }
    }

    public boolean handleMouseScroll(
            Minecraft client,
            long window,
            double verticalOffset
    ) {
        Objects.requireNonNull(client, "client");
        return isActive()
                && (straightRoadTool.handleMouseScroll(
                        client,
                        window,
                        verticalOffset
                ) || cameraController.handleMouseScroll(
                        client,
                        window,
                        verticalOffset
                ));
    }

    public boolean handleEscape(Minecraft client, long window, int key, int action) {
        Objects.requireNonNull(client, "client");
        if (!isActive()
                || window != client.getWindow().getWindow()
                || key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                || action != org.lwjgl.glfw.GLFW.GLFW_PRESS
                || client.screen != null
                || client.getOverlay() != null) {
            return false;
        }

        if (!straightRoadTool.cancelInteractiveStep()) {
            exit(client, RoadBuilderModeExitReason.USER_TOGGLED);
        }
        return true;
    }

    public boolean handleKeyboard(
            Minecraft client,
            long window,
            int key,
            int action,
            int modifiers
    ) {
        if (handleEscape(client, window, key, action)) {
            return true;
        }
        return isActive()
                && window == client.getWindow().getWindow()
                && client.screen == null
                && client.getOverlay() == null
                && straightRoadTool.handleToolbarKey(
                        client,
                        key,
                        action,
                        modifiers
                );
    }

    private static void drainConflictingActionClicks(Minecraft client) {
        consumeAll(client.options.keyAttack);
        consumeAll(client.options.keyUse);
        consumeAll(client.options.keyPickItem);
        consumeAll(client.options.keyInventory);
        consumeAll(client.options.keyDrop);
        consumeAll(client.options.keySwapOffhand);
        consumeAll(client.options.keyTogglePerspective);
        consumeAll(client.options.keySmoothCamera);
        consumeAll(client.options.keyAdvancements);
        consumeAll(client.options.keySaveHotbarActivator);
        consumeAll(client.options.keyLoadHotbarActivator);
        for (net.minecraft.client.KeyMapping hotbarSlot : client.options.keyHotbarSlots) {
            consumeAll(hotbarSlot);
        }
    }

    private static void consumeAll(net.minecraft.client.KeyMapping mapping) {
        while (mapping.consumeClick()) {
            // Prevent a Road Builder key press from firing after mode exit.
        }
    }

    private void restoreMouseWhenReady(Minecraft client) {
        if (!pendingMouseRestore || !client.isWindowActive() || client.screen != null) {
            return;
        }

        client.mouseHandler.grabMouse();
        pendingMouseRestore = false;
    }
}
