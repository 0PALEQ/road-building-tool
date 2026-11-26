package com.cookiecraftmods.roadbuilder.mixin.client;

import com.cookiecraftmods.roadbuilder.client.RoadBuilderClient;
import com.cookiecraftmods.roadbuilder.client.camera.CityBuilderCameraPose;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("TAIL"))
    private void roadbuilder$applyCityBuilderPose(
            Level level,
            Entity focusEntity,
            boolean detached,
            boolean mirrored,
            float partialTick,
            CallbackInfo callback
    ) {
        RoadBuilderClient.modeController()
                .cameraPose(focusEntity, partialTick)
                .ifPresent(this::roadbuilder$applyPose);
    }

    private void roadbuilder$applyPose(CityBuilderCameraPose pose) {
        setRotation(pose.yaw(), pose.pitch());
        setPosition(pose.position().x, pose.position().y, pose.position().z);
    }
}
