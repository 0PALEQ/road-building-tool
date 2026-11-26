package com.cookiecraftmods.roadbuilder.mixin.client;

import com.cookiecraftmods.roadbuilder.client.render.RoadWorldProjection;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin implements RoadWorldProjection {
    @Unique
    private Matrix4f roadbuilder$worldProjection;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void roadbuilder$captureProjection(
            GraphicsResourceAllocator allocator, DeltaTracker ticks, boolean outline, Camera camera,
            Matrix4f view, Matrix4f projection, Matrix4f cullProjection, GpuBufferSlice fog,
            Vector4f fogColor, boolean renderSky, CallbackInfo callback
    ) {
        roadbuilder$worldProjection = projection;
    }

    @Override
    public Matrix4fc roadbuilder$projection() {
        return roadbuilder$worldProjection;
    }
}
