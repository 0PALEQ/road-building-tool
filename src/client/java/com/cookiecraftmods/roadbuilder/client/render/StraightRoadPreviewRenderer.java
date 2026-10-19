package com.cookiecraftmods.roadbuilder.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.cookiecraftmods.roadbuilder.client.tool.RawRoadPreview;
import com.cookiecraftmods.roadbuilder.client.tool.StraightRoadToolController;
import com.cookiecraftmods.roadbuilder.client.tool.RoadSnapGuide;
import com.cookiecraftmods.roadbuilder.client.tool.RoadNodeMarker;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

public final class StraightRoadPreviewRenderer {
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

    public void render(WorldRenderContext context, StraightRoadToolController tool) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(tool, "tool");
        MultiBufferSource consumers = context.consumers();
        if (consumers == null) {
            return;
        }

        PoseStack matrices = context.matrixStack();
        Vec3 cameraPosition = context.camera().getPosition();
        matrices.pushPose();
        matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        try {
            float previewRed = tool.placementPending() ? 0.95F : 0.15F;
            float previewGreen = tool.placementPending() ? 0.62F : 0.72F;
            float previewBlue = tool.placementPending() ? 0.12F : 0.95F;
            tool.rawPreviews().forEach(preview -> {
                VertexConsumer fill = consumers.getBuffer(NODE_FILL);
                List<RawRoadPreview.CrossSection> sections =
                        preview.crossSections();
                for (int index = 0; index < sections.size() - 1; index++) {
                    renderPreviewQuad(
                            matrices,
                            fill,
                            sections.get(index),
                            sections.get(index + 1),
                            previewRed,
                            previewGreen,
                            previewBlue
                    );
                }
                VertexConsumer edgeLines = consumers.getBuffer(
                        RenderType.lines()
                );
                for (int index = 0; index < sections.size() - 1; index++) {
                    renderPreviewEdge(
                            matrices,
                            edgeLines,
                            sections.get(index),
                            sections.get(index + 1),
                            previewRed,
                            previewGreen,
                            previewBlue
                    );
                }
            });
            VertexConsumer lines = consumers.getBuffer(RenderType.lines());
            tool.snapGuides().forEach(guide ->
                    renderSnapGuide(matrices, lines, guide));

            for (BlockPos conflict : tool.removalConflictSamples()) {
                DebugRenderer.renderFilledBox(
                        matrices,
                        consumers,
                        surfaceOverlay(conflict),
                        0.95F,
                        0.12F,
                        0.12F,
                        0.55F
                );
            }

            tool.removalHighlightGeometry().ifPresent(geometry ->
                    renderSegmentHighlight(matrices, consumers, geometry)
            );

            var nodeMarkers = tool.visibleNodeMarkers();
            VertexConsumer nodeFill = consumers.getBuffer(
                    NODE_FILL
            );
            for (RoadNodeMarker marker : nodeMarkers) {
                renderNodeMarkerFill(matrices, nodeFill, marker);
            }
            VertexConsumer markerLines = consumers.getBuffer(
                    RenderType.lines()
            );
            for (RoadNodeMarker marker : nodeMarkers) {
                renderNodeMarkerOutline(matrices, markerLines, marker);
            }
            tool.startBlock().ifPresent(position -> LevelRenderer.renderLineBox(
                    matrices,
                    markerLines,
                    new AABB(position).inflate(0.004),
                    0.1F,
                    0.9F,
                    1.0F,
                    1.0F
            ));
            tool.cursorBlock().ifPresent(position -> LevelRenderer.renderLineBox(
                    matrices,
                    markerLines,
                    new AABB(position).inflate(0.008),
                    1.0F,
                    0.85F,
                    0.15F,
                    1.0F
            ));
        } finally {
            matrices.popPose();
        }
    }

    private static void renderNodeMarkerFill(
            PoseStack matrices,
            VertexConsumer fill,
            RoadNodeMarker marker
    ) {
        RoadNode node = marker.node();
        final int segmentCount = 32;
        final double radius = marker.radius();
        final double y = node.position().y() + 1.068;
        for (int index = 0; index < segmentCount; index++) {
            filledVertex(
                    matrices,
                    fill,
                    node.position().x(),
                    y,
                    node.position().z(),
                    marker.junction()
            );
            circleVertex(
                    matrices,
                    fill,
                    node,
                    radius,
                    y,
                    index,
                    marker.junction()
            );
            circleVertex(
                    matrices,
                    fill,
                    node,
                    radius,
                    y,
                    index + 1,
                    marker.junction()
            );
        }
    }

    private static void circleVertex(
            PoseStack matrices,
            VertexConsumer fill,
            RoadNode node,
            double radius,
            double y,
            int index,
            boolean junction
    ) {
        final int segmentCount = 32;
        double angle = Math.PI * 2.0 * index / segmentCount;
        filledVertex(
                matrices,
                fill,
                node.position().x() + Math.cos(angle) * radius,
                y,
                node.position().z() + Math.sin(angle) * radius,
                junction
        );
    }

    private static void renderNodeMarkerOutline(
            PoseStack matrices,
            VertexConsumer lines,
            RoadNodeMarker marker
    ) {
        RoadNode node = marker.node();
        final int segmentCount = 24;
        final double radius = marker.radius();
        final double y = node.position().y() + 1.075;
        for (int index = 0; index < segmentCount; index++) {
            double startAngle = Math.PI * 2.0 * index / segmentCount;
            double endAngle = Math.PI * 2.0 * (index + 1) / segmentCount;
            line(
                    matrices,
                    lines,
                    node.position().x() + Math.cos(startAngle) * radius,
                    y,
                    node.position().z() + Math.sin(startAngle) * radius,
                    node.position().x() + Math.cos(endAngle) * radius,
                    y,
                    node.position().z() + Math.sin(endAngle) * radius,
                    marker.junction() ? 1.0F : 0.12F,
                    marker.junction() ? 0.72F : 0.82F,
                    marker.junction() ? 0.16F : 1.0F,
                    0.72F
            );
        }
    }

    private static void renderPreviewQuad(
            PoseStack matrices,
            VertexConsumer fill,
            RawRoadPreview.CrossSection first,
            RawRoadPreview.CrossSection second,
            float red,
            float green,
            float blue
    ) {
        previewVertex(matrices, fill, first.left(), red, green, blue);
        previewVertex(matrices, fill, first.right(), red, green, blue);
        previewVertex(matrices, fill, second.left(), red, green, blue);
        previewVertex(matrices, fill, first.right(), red, green, blue);
        previewVertex(matrices, fill, second.right(), red, green, blue);
        previewVertex(matrices, fill, second.left(), red, green, blue);
    }

    private static void previewVertex(
            PoseStack matrices,
            VertexConsumer fill,
            RoadPoint point,
            float red,
            float green,
            float blue
    ) {
        fill.vertex(
                        matrices.last().pose(),
                        (float) point.x(),
                        (float) (point.y() + 1.02),
                        (float) point.z()
                )
                .color(red, green, blue, 0.28F)
                .endVertex();
    }

    private static void renderPreviewEdge(
            PoseStack matrices,
            VertexConsumer lines,
            RawRoadPreview.CrossSection first,
            RawRoadPreview.CrossSection second,
            float red,
            float green,
            float blue
    ) {
        line(
                matrices,
                lines,
                first.left().x(),
                first.left().y() + 1.02,
                first.left().z(),
                second.left().x(),
                second.left().y() + 1.02,
                second.left().z(),
                red,
                green,
                blue,
                0.9F
        );
        line(
                matrices,
                lines,
                first.right().x(),
                first.right().y() + 1.02,
                first.right().z(),
                second.right().x(),
                second.right().y() + 1.02,
                second.right().z(),
                red,
                green,
                blue,
                0.9F
        );
    }

    private static void filledVertex(
            PoseStack matrices,
            VertexConsumer fill,
            double x,
            double y,
            double z,
            boolean junction
    ) {
        fill.vertex(
                        matrices.last().pose(),
                        (float) x,
                        (float) y,
                        (float) z
                )
                .color(
                        junction ? 1.0F : 0.12F,
                        junction ? 0.72F : 0.82F,
                        junction ? 0.16F : 1.0F,
                        0.08F
                )
                .endVertex();
    }

    private static void renderSnapGuide(
            PoseStack matrices,
            VertexConsumer lines,
            RoadSnapGuide guide
    ) {
        double deltaX = guide.end().x() - guide.start().x();
        double deltaY = guide.end().y() - guide.start().y();
        double deltaZ = guide.end().z() - guide.start().z();
        double length = Math.hypot(deltaX, deltaZ);
        double dashPeriod = 1.35;
        double dashLength = 0.78;
        float red = guide.kind() == RoadSnapGuide.Kind.LANE_CONTINUATION
                ? 0.20F : 1.0F;
        float green = guide.kind() == RoadSnapGuide.Kind.LANE_CONTINUATION
                ? 0.92F : 0.78F;
        float blue = guide.kind() == RoadSnapGuide.Kind.LANE_CONTINUATION
                ? 1.0F : 0.18F;
        for (double distance = 0.0;
                distance < length;
                distance += dashPeriod) {
            double startAmount = distance / length;
            double endAmount = Math.min(
                    length,
                    distance + dashLength
            ) / length;
            line(
                    matrices,
                    lines,
                    guide.start().x() + deltaX * startAmount,
                    guide.start().y() + deltaY * startAmount + 1.085,
                    guide.start().z() + deltaZ * startAmount,
                    guide.start().x() + deltaX * endAmount,
                    guide.start().y() + deltaY * endAmount + 1.085,
                    guide.start().z() + deltaZ * endAmount,
                    red,
                    green,
                    blue,
                    0.95F
            );
        }
    }

    private static void line(
            PoseStack matrices,
            VertexConsumer lines,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ
    ) {
        line(
                matrices,
                lines,
                startX,
                startY,
                startZ,
                endX,
                endY,
                endZ,
                1.0F,
                0.12F,
                0.12F,
                1.0F
        );
    }

    private static void line(
            PoseStack matrices,
            VertexConsumer lines,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        lines.vertex(matrices.last().pose(), (float) startX, (float) startY, (float) startZ)
                .color(red, green, blue, alpha)
                .normal(matrices.last().normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
        lines.vertex(matrices.last().pose(), (float) endX, (float) endY, (float) endZ)
                .color(red, green, blue, alpha)
                .normal(matrices.last().normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private static AABB surfaceOverlay(BlockPos position) {
        return new AABB(
                position.getX() + 0.02,
                position.getY() + 1.003,
                position.getZ() + 0.02,
                position.getX() + 0.98,
                position.getY() + 1.035,
                position.getZ() + 0.98
        );
    }

    private static void renderSegmentHighlight(
            PoseStack matrices,
            MultiBufferSource consumers,
            ReferenceGeometry geometry
    ) {
        double length = geometry.length();
        int sampleCount = SegmentHighlightSampling.sampleCount(length);
        for (int index = 0; index <= sampleCount; index++) {
            double distance = SegmentHighlightSampling.distance(length, sampleCount, index);
            var point = geometry.sample(distance).position();
            DebugRenderer.renderFilledBox(
                    matrices,
                    consumers,
                    new AABB(
                            point.x() - 0.16,
                            point.y() + 1.04,
                            point.z() - 0.16,
                            point.x() + 0.16,
                            point.y() + 1.16,
                            point.z() + 0.16
                    ),
                    0.95F,
                    0.25F,
                    0.12F,
                    0.72F
            );
        }
    }
}
