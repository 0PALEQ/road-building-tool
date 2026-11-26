package com.cookiecraftmods.roadbuilder.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public final class RoadWorldRendering {
    private static final RenderType NODE_FILL = new RenderType(
            "roadbuilder_node_fill",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
            },
            () -> {
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
            }
    ) { };

    private RoadWorldRendering() {
    }

    public static void afterEntities(Consumer<RoadWorldRenderFrame> render) {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            var matrices = context.matrixStack();
            if (matrices != null) {
                Matrix4f projection = new Matrix4f(context.projectionMatrix())
                        .mul(RenderSystem.getModelViewMatrix()).mul(matrices.last().pose());
                render.accept(new RoadWorldRenderFrame(
                        matrices, context.consumers(), context.camera().getPosition(), projection
                ));
            }
        });
    }

    static VertexConsumer fill(MultiBufferSource consumers) {
        return consumers.getBuffer(NODE_FILL);
    }

    static VertexConsumer lines(MultiBufferSource consumers) {
        return consumers.getBuffer(RenderType.lines());
    }

    static void lineBox(PoseStack matrices, VertexConsumer vertices, AABB box,
                        float red, float green, float blue, float alpha) {
        LevelRenderer.renderLineBox(matrices, vertices, box, red, green, blue, alpha);
    }

    static void filledBox(PoseStack matrices, MultiBufferSource consumers, AABB box,
                          float red, float green, float blue, float alpha) {
        DebugRenderer.renderFilledBox(matrices, consumers, box, red, green, blue, alpha);
    }
}
