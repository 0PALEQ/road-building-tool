package com.cookiecraftmods.roadbuilder.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public record RoadWorldRenderFrame(
        PoseStack matrixStack, MultiBufferSource consumers, Vec3 cameraPosition, Matrix4f projection
) {
}
