package com.cookiecraftmods.roadbuilder.client.render;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;
import com.cookiecraftmods.roadbuilder.mixin.client.RenderTypeInvoker;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public final class RoadWorldRendering {
    private static final RenderPipeline PREVIEW_PIPELINE = RenderPipeline.builder()
            .withLocation(MinecraftIds.id("roadbuilder", "pipeline/road_preview"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .build();
    private static final RenderType NODE_FILL = RenderTypeInvoker.roadbuilder$create(
            "roadbuilder_node_fill", RenderSetup.builder(PREVIEW_PIPELINE).createRenderSetup()
    );

    private RoadWorldRendering() {
    }

    public static void afterEntities(Consumer<RoadWorldRenderFrame> render) {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            var matrices = context.matrices();
            var levelRenderer = (RoadWorldProjection) context.worldRenderer();
            Matrix4f projection = new Matrix4f(levelRenderer.roadbuilder$projection())
                    .mul(RenderSystem.getModelViewMatrix()).mul(matrices.last().pose());
            render.accept(new RoadWorldRenderFrame(
                    matrices, context.consumers(), context.worldState().cameraRenderState.pos, projection
            ));
        });
    }

    static VertexConsumer fill(MultiBufferSource consumers) {
        return consumers.getBuffer(NODE_FILL);
    }

    static VertexConsumer lines(MultiBufferSource consumers) {
        return consumers.getBuffer(RenderTypes.lines());
    }

    static void lineBox(PoseStack matrices, VertexConsumer vertices, AABB box,
                        float red, float green, float blue, float alpha) {
        ShapeRenderer.renderShape(matrices, vertices, Shapes.create(box), 0, 0, 0,
                ARGB.colorFromFloat(alpha, red, green, blue), RoadVertices.lineWidth());
    }

    static void filledBox(PoseStack matrices, MultiBufferSource consumers, AABB box,
                          float red, float green, float blue, float alpha) {
        var vertices = consumers.getBuffer(RenderTypes.debugQuads());
        var pose = matrices.last();
        int color = ARGB.colorFromFloat(alpha, red, green, blue);
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        vertices.addVertex(pose, x0, y0, z0).setColor(color);
        vertices.addVertex(pose, x0, y1, z0).setColor(color);
        vertices.addVertex(pose, x1, y1, z0).setColor(color);
        vertices.addVertex(pose, x1, y0, z0).setColor(color);

        vertices.addVertex(pose, x0, y0, z1).setColor(color);
        vertices.addVertex(pose, x1, y0, z1).setColor(color);
        vertices.addVertex(pose, x1, y1, z1).setColor(color);
        vertices.addVertex(pose, x0, y1, z1).setColor(color);

        vertices.addVertex(pose, x0, y0, z0).setColor(color);
        vertices.addVertex(pose, x0, y0, z1).setColor(color);
        vertices.addVertex(pose, x0, y1, z1).setColor(color);
        vertices.addVertex(pose, x0, y1, z0).setColor(color);

        vertices.addVertex(pose, x1, y0, z0).setColor(color);
        vertices.addVertex(pose, x1, y1, z0).setColor(color);
        vertices.addVertex(pose, x1, y1, z1).setColor(color);
        vertices.addVertex(pose, x1, y0, z1).setColor(color);

        vertices.addVertex(pose, x0, y0, z0).setColor(color);
        vertices.addVertex(pose, x1, y0, z0).setColor(color);
        vertices.addVertex(pose, x1, y0, z1).setColor(color);
        vertices.addVertex(pose, x0, y0, z1).setColor(color);

        vertices.addVertex(pose, x0, y1, z0).setColor(color);
        vertices.addVertex(pose, x0, y1, z1).setColor(color);
        vertices.addVertex(pose, x1, y1, z1).setColor(color);
        vertices.addVertex(pose, x1, y1, z0).setColor(color);
    }
}
