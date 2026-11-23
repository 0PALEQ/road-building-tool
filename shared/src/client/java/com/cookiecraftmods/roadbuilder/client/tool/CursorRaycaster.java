package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.client.RoadClientRender;

import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.mixin.client.GameRendererAccessor;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

public final class CursorRaycaster {
    private static final double RAY_DISTANCE = ConstructionLimits.DEFAULT_MAXIMUM_DISTANCE;

    private CursorRaycaster() {
    }

    public static CursorTargetResult cast(Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (client.level == null || client.player == null) {
            return CursorTargetResult.failure(CursorTargetProblem.NO_WORLD);
        }

        Optional<CursorRay> ray = ray(client);
        if (ray.isEmpty()) {
            return CursorTargetResult.failure(CursorTargetProblem.NO_WORLD);
        }
        CursorRay cursorRay = ray.orElseThrow();
        Vec3 origin = new Vec3(
                cursorRay.origin().x(),
                cursorRay.origin().y(),
                cursorRay.origin().z()
        );
        Vec3 direction = new Vec3(
                cursorRay.direction().x(),
                cursorRay.direction().y(),
                cursorRay.direction().z()
        );
        Vec3 destination = origin.add(direction.scale(cursorRay.maximumDistance()));
        BlockHitResult hit = client.level.clip(new ClipContext(
                origin,
                destination,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                client.player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return CursorTargetResult.failure(CursorTargetProblem.MISS);
        }
        return CursorTargetResult.success(target(hit));
    }

    static CursorTarget target(BlockHitResult hit) {
        return CursorTarget.at(
                Objects.requireNonNull(hit, "hit").getBlockPos()
        );
    }

    public static Optional<CursorRay> ray(Minecraft client) {
        Objects.requireNonNull(client, "client");
        if (client.level == null || client.player == null) {
            return Optional.empty();
        }
        Camera camera = client.gameRenderer.getMainCamera();
        Window window = client.getWindow();
        if (!camera.isInitialized()
                || window.getScreenWidth() <= 0
                || window.getScreenHeight() <= 0) {
            return Optional.empty();
        }

        double normalizedX = client.mouseHandler.xpos()
                / window.getScreenWidth() * 2.0 - 1.0;
        double normalizedY = 1.0 - client.mouseHandler.ypos()
                / window.getScreenHeight() * 2.0;
        double aspectRatio = window.getWidth() / (double) window.getHeight();
        double verticalFov = ((GameRendererAccessor) client.gameRenderer)
                .roadbuilder$getFov(camera, RoadClientRender.partialTick(client), true);
        Vec3 direction = projectedDirection(
                new Vec3(camera.getLookVector()),
                new Vec3(camera.getLeftVector()),
                new Vec3(camera.getUpVector()),
                normalizedX,
                normalizedY,
                aspectRatio,
                verticalFov
        );
        Vec3 origin = camera.getPosition();
        return Optional.of(new CursorRay(
                new RoadPoint(origin.x, origin.y, origin.z),
                new RoadVector(direction.x, direction.y, direction.z),
                RAY_DISTANCE
        ));
    }

    static Vec3 projectedDirection(
            Vec3 forward,
            Vec3 left,
            Vec3 up,
            double normalizedX,
            double normalizedY,
            double aspectRatio,
            double verticalFovDegrees
    ) {
        if (!Double.isFinite(normalizedX)
                || !Double.isFinite(normalizedY)
                || !Double.isFinite(aspectRatio)
                || aspectRatio <= 0.0
                || !Double.isFinite(verticalFovDegrees)
                || verticalFovDegrees <= 0.0
                || verticalFovDegrees >= 180.0) {
            throw new IllegalArgumentException("invalid projection parameters");
        }
        double halfHeight = Math.tan(Math.toRadians(verticalFovDegrees) / 2.0);
        double halfWidth = halfHeight * aspectRatio;
        return forward
                .subtract(left.scale(normalizedX * halfWidth))
                .add(up.scale(normalizedY * halfHeight))
                .normalize();
    }
}
