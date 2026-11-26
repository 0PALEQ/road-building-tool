package com.cookiecraftmods.roadbuilder.client.camera;

import com.cookiecraftmods.roadbuilder.client.config.RoadBuilderClientPreferences;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

public final class CityBuilderCameraController {
    private static final float DEFAULT_PITCH = 55.0F;
    private static final double DEFAULT_DISTANCE = 24.0;
    private static final double FOCUS_HEIGHT = 0.5;
    private static final double BORDER_MARGIN = 0.5;
    private static final double COLLISION_MARGIN = 1.0 + 1.0E-7;

    private RoadBuilderClientPreferences preferences;
    private boolean active;
    private CameraType previousCameraType;
    private LocalPlayer entryPlayer;
    private boolean previousNoGravity;
    private float yaw;
    private float pitch = DEFAULT_PITCH;
    private double distance = DEFAULT_DISTANCE;
    private boolean rotating;
    private double lastCursorX;
    private double lastCursorY;

    public CityBuilderCameraController(RoadBuilderClientPreferences preferences) {
        setPreferences(preferences);
    }

    public void setPreferences(RoadBuilderClientPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    public void enter(Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (active || client.player == null) {
            return;
        }

        active = true;
        previousCameraType = client.options.getCameraType();
        client.options.setCameraType(CameraType.FIRST_PERSON);
        entryPlayer = client.player;
        previousNoGravity = entryPlayer.isNoGravity();
        entryPlayer.setNoGravity(true);
        entryPlayer.setDeltaMovement(Vec3.ZERO);
        yaw = Mth.wrapDegrees(entryPlayer.getYRot());
        pitch = DEFAULT_PITCH;
        distance = DEFAULT_DISTANCE;
        rotating = false;
    }

    public void exit(Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (!active) {
            return;
        }

        active = false;
        rotating = false;
        if (previousCameraType != null) {
            client.options.setCameraType(previousCameraType);
        }
        previousCameraType = null;
        if (entryPlayer != null) {
            entryPlayer.setNoGravity(previousNoGravity);
            entryPlayer.setDeltaMovement(Vec3.ZERO);
        }
        entryPlayer = null;
    }

    public void tick(Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (!active || client.player == null || client.level == null) {
            return;
        }

        LocalPlayer player = client.player;
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        if (!client.isWindowActive() || client.screen != null || client.getOverlay() != null) {
            rotating = false;
            return;
        }

        Options options = client.options;
        double forwardAxis = axis(options.keyUp.isDown(), options.keyDown.isDown());
        double rightAxis = CityBuilderCameraMath.horizontalMovementAxis(
                options.keyRight.isDown(),
                options.keyLeft.isDown(),
                preferences.invertHorizontalMovement()
        );
        double verticalAxis = axis(options.keyInventory.isDown(), options.keyDrop.isDown());
        boolean fast = options.keySprint.isDown();
        double speed = CityBuilderCameraMath.panSpeed(distance, fast);
        CityBuilderCameraMath.HorizontalDelta horizontal =
                CityBuilderCameraMath.horizontalDelta(yaw, forwardAxis, rightAxis, speed);

        if (horizontal.equals(CityBuilderCameraMath.HorizontalDelta.ZERO)
                && verticalAxis == 0.0) {
            return;
        }

        WorldBorder border = client.level.getWorldBorder();
        double minimumX = border.getMinX() + BORDER_MARGIN;
        double maximumX = border.getMaxX() - BORDER_MARGIN;
        double minimumZ = border.getMinZ() + BORDER_MARGIN;
        double maximumZ = border.getMaxZ() - BORDER_MARGIN;
        double minimumY = client.level.getMinBuildHeight() + 1.0;
        double maximumY = client.level.getMaxBuildHeight() - 2.0;

        double nextX = clamp(player.getX() + horizontal.x(), minimumX, maximumX);
        double nextY = clamp(player.getY() + verticalAxis * speed, minimumY, maximumY);
        double nextZ = clamp(player.getZ() + horizontal.z(), minimumZ, maximumZ);
        if (!hasLoadedMovementChunks(
                player.getBoundingBox(),
                nextX - player.getX(),
                nextZ - player.getZ(),
                client.level.getChunkSource()::hasChunk
        )) {
            return;
        }
        player.setPos(nextX, nextY, nextZ);
    }

    static boolean hasLoadedMovementChunks(
            AABB playerBounds,
            double movementX,
            double movementZ,
            BiPredicate<Integer, Integer> hasChunk
    ) {
        // Collision checks can inspect neighboring blocks outside the swept player bounds.
        AABB collisionBounds = playerBounds.expandTowards(movementX, 0.0, movementZ)
                .inflate(COLLISION_MARGIN, 0.0, COLLISION_MARGIN);
        int minChunkX = SectionPos.blockToSectionCoord(collisionBounds.minX);
        int maxChunkX = SectionPos.blockToSectionCoord(collisionBounds.maxX);
        int minChunkZ = SectionPos.blockToSectionCoord(collisionBounds.minZ);
        int maxChunkZ = SectionPos.blockToSectionCoord(collisionBounds.maxZ);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!hasChunk.test(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean handleMouseButton(
            Minecraft client,
            long windowHandle,
            int button,
            int action
    ) {
        Objects.requireNonNull(client, "client");
        if (!active
                || button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || windowHandle != client.getWindow().getWindow()
                || client.screen != null
                || client.getOverlay() != null) {
            return false;
        }

        if (action == GLFW.GLFW_PRESS) {
            rotating = true;
            lastCursorX = client.mouseHandler.xpos();
            lastCursorY = client.mouseHandler.ypos();
        } else if (action == GLFW.GLFW_RELEASE) {
            rotating = false;
        }
        return true;
    }

    public void handleCursorPosition(Minecraft client, long windowHandle, double x, double y) {
        Objects.requireNonNull(client, "client");
        if (!active
                || !rotating
                || windowHandle != client.getWindow().getWindow()
                || client.screen != null
                || client.getOverlay() != null) {
            return;
        }

        double deltaX = x - lastCursorX;
        double deltaY = y - lastCursorY;
        lastCursorX = x;
        lastCursorY = y;
        double degreesPerPixel = CityBuilderCameraMath.rotationDegreesPerPixel(
                client.options.sensitivity().get()
        );
        yaw = CityBuilderCameraMath.rotatedYaw(
                yaw,
                deltaX,
                degreesPerPixel,
                preferences.invertHorizontalMouseDrag()
        );
        pitch = CityBuilderCameraMath.rotatedPitch(
                pitch,
                deltaY,
                degreesPerPixel,
                preferences.invertVerticalMouseDrag()
        );
    }

    public boolean handleMouseScroll(
            Minecraft client,
            long windowHandle,
            double verticalOffset
    ) {
        Objects.requireNonNull(client, "client");
        if (!active
                || windowHandle != client.getWindow().getWindow()
                || client.screen != null
                || client.getOverlay() != null) {
            return false;
        }

        double scrollSteps = client.options.discreteMouseScroll().get()
                ? Math.signum(verticalOffset)
                : verticalOffset * client.options.mouseWheelSensitivity().get();
        distance = CityBuilderCameraMath.zoomedDistance(distance, scrollSteps);
        return true;
    }

    public Optional<CityBuilderCameraPose> pose(Entity focusEntity, float partialTick) {
        Objects.requireNonNull(focusEntity, "focusEntity");
        if (!active) {
            return Optional.empty();
        }

        Vec3 focus = focusEntity.getPosition(partialTick).add(0.0, FOCUS_HEIGHT, 0.0);
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double horizontalScale = Math.cos(pitchRadians);
        Vec3 forward = new Vec3(
                -Math.sin(yawRadians) * horizontalScale,
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * horizontalScale
        );
        return Optional.of(new CityBuilderCameraPose(
                focus.subtract(forward.scale(distance)),
                yaw,
                pitch
        ));
    }

    public boolean isActive() {
        return active;
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1.0 : 0.0) - (negative ? 1.0 : 0.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (minimum > maximum) {
            return (minimum + maximum) * 0.5;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
