package com.cookiecraftmods.roadbuilder.client.camera;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record CityBuilderCameraPose(Vec3 position, float yaw, float pitch) {
    public CityBuilderCameraPose {
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(position.x)
                || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Camera pose must be finite");
        }
    }
}
