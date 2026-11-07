package com.cookiecraftmods.roadbuilder.client.render;

import com.cookiecraftmods.roadbuilder.client.tool.RawRoadPreview;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.List;
import com.cookiecraftmods.roadbuilder.client.tool.StraightRoadToolController.AttachmentAngle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;

/** World-anchored, camera-facing measurements for the primary road preview. */
public final class RoadPreviewMeasurementsRenderer {
    private static final RenderType GUIDES = new RenderType(
            "roadbuilder_measurement_guides", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 4096, false, true,
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
            }, () -> {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
            }) { };

    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(
            new com.mojang.blaze3d.vertex.BufferBuilder(4096));

    private RoadPreviewMeasurementsRenderer() { }

    /** Draw after water/translucency, using owned buffers to preserve panel/text ordering. */
    public static void renderOverlay(WorldRenderContext context,
            com.cookiecraftmods.roadbuilder.client.tool.StraightRoadToolController tool) {
        var preview = tool.rawPreview();
        if (preview.isEmpty()) return;
        PoseStack poses = context.matrixStack();
        var camera = context.camera().getPosition();
        poses.pushPose();
        try {
            poses.translate(-camera.x, -camera.y, -camera.z);
            render(context, poses, BUFFERS, preview.get(), tool.previewAttachmentAngles());
        } finally {
            BUFFERS.endBatch();
            poses.popPose();
        }
    }

    static void render(WorldRenderContext context, PoseStack poses,
                       MultiBufferSource.BufferSource buffers, RawRoadPreview preview, List<AttachmentAngle> attachments) {
        var geometry = preview.geometry();
        double length = geometry.length();
        var middle = geometry.sample(length / 2.0);
        var tangent = middle.tangent();
        double horizontal = tangent.horizontalLength();
        double offset = preview.width() / 2.0 + 2.0;
        RoadPoint anchor = new RoadPoint(
                middle.position().x() + (horizontal > 1.0e-9 ? tangent.z() / horizontal : 1.0) * offset,
                middle.position().y() + 2.0,
                middle.position().z() - (horizontal > 1.0e-9 ? tangent.x() / horizontal : 0.0) * offset);
        double rise = geometry.end().y() - geometry.start().y();
        double run = preview.horizontalLength();
        String grade = run > 1.0e-9 ? String.format(Locale.ROOT, "%+.1f%%", rise / run * 100.0) : "—";
        label(context, poses, buffers, anchor, new Component[] {
                Component.translatable("preview.roadbuilder.length", decimal(length)),
                Component.translatable("preview.roadbuilder.elevation", decimal(geometry.end().y()),
                        String.format(Locale.ROOT, "%+.1f", rise)),
                Component.translatable("preview.roadbuilder.grade", grade)
        });

        for (AttachmentAngle attachment : attachments) {
            var road = attachment.roadDirection();
            var branch = attachment.previewDirection();
            if (road.horizontalLength() < 1.0e-9 || branch.horizontalLength() < 1.0e-9) continue;
            angle(context, poses, buffers, preview.width(), attachment.point(),
                    Math.atan2(road.z(), road.x()), Math.atan2(branch.z(), branch.x()));
        }
        // Preserve the free-end bend guide, but don't overlay it on an attachment guide.
        if (attachments.stream().noneMatch(a -> a.point().distanceTo(geometry.end()) < 0.05)) {
            var entry = geometry.sample(0.0).tangent();
            var exit = geometry.sample(length).tangent();
            if (entry.horizontalLength() > 1.0e-9 && exit.horizontalLength() > 1.0e-9) {
                angle(context, poses, buffers, preview.width(), geometry.end(),
                        Math.atan2(-entry.z(), -entry.x()), Math.atan2(exit.z(), exit.x()));
            }
        }
    }

    private static void angle(WorldRenderContext context, PoseStack poses, MultiBufferSource.BufferSource buffers,
                              double width, RoadPoint pivot, double from, double to) {
        double sweep = Math.atan2(Math.sin(to - from), Math.cos(to - from));
        double radius = width / 2.0 + 3.0;
        double distance = context.camera().getPosition().distanceTo(
                new net.minecraft.world.phys.Vec3(pivot.x(), pivot.y(), pivot.z()));
        double thickness = Math.max(0.12, distance * 0.003);
        VertexConsumer lines = buffers.getBuffer(GUIDES);
        for (int i = 0; i < 32; i++) {
            double a = from + sweep * i / 32.0;
            double b = from + sweep * (i + 1) / 32.0;
            segment(poses, lines, pivot, radius, a, radius, b, thickness);
        }
        segment(poses, lines, pivot, 0.0, from, radius + 1.0, from, thickness);
        segment(poses, lines, pivot, 0.0, to, radius + 1.0, to, thickness);
        double bisector = from + sweep / 2.0;
        label(context, poses, buffers, new RoadPoint(
                pivot.x() + Math.cos(bisector) * (radius + 2.0), pivot.y() + 2.0,
                pivot.z() + Math.sin(bisector) * (radius + 2.0)),
                new Component[] {Component.translatable("preview.roadbuilder.angle",
                        decimal(Math.toDegrees(Math.abs(sweep))))});
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void segment(PoseStack poses, VertexConsumer lines, RoadPoint pivot,
                                double r1, double a, double r2, double b, double thickness) {
        double x1 = pivot.x() + r1 * Math.cos(a), z1 = pivot.z() + r1 * Math.sin(a);
        double x2 = pivot.x() + r2 * Math.cos(b), z2 = pivot.z() + r2 * Math.sin(b);
        double length = Math.hypot(x2 - x1, z2 - z1);
        if (length < 1.0e-9) return;
        double nx = -(z2 - z1) / length * thickness / 2;
        double nz = (x2 - x1) / length * thickness / 2;
        double y = pivot.y() + 1.2;
        vertex(poses, lines, x1 + nx, y, z1 + nz);
        vertex(poses, lines, x1 - nx, y, z1 - nz);
        vertex(poses, lines, x2 + nx, y, z2 + nz);
        vertex(poses, lines, x2 + nx, y, z2 + nz);
        vertex(poses, lines, x1 - nx, y, z1 - nz);
        vertex(poses, lines, x2 - nx, y, z2 - nz);
    }

    private static void vertex(PoseStack poses, VertexConsumer lines, double x, double y, double z) {
        lines.vertex(poses.last().pose(), (float) x, (float) y, (float) z)
                .color(0.95F, 0.98F, 1.0F, 1.0F).endVertex();
    }

    private static void panelVertex(PoseStack poses, VertexConsumer panel, float x, float y) {
        panel.vertex(poses.last().pose(), x, y, 0).color(0.04F, 0.06F, 0.08F, 0.95F).endVertex();
    }

    private static void label(WorldRenderContext context, PoseStack poses, MultiBufferSource.BufferSource buffers,
                              RoadPoint point, Component[] rows) {
        Font font = Minecraft.getInstance().font;
        double distance = context.camera().getPosition().distanceTo(
                new net.minecraft.world.phys.Vec3(point.x(), point.y(), point.z()));
        float scale = (float) Math.max(0.06, distance * 0.003);
        poses.pushPose();
        try {
            poses.translate(point.x(), point.y(), point.z());
            poses.mulPose(context.camera().rotation());
            poses.scale(-scale, -scale, scale);
            // The see-through font's built-in background can be batched after glyphs
            // (especially with shadows). Flush an explicit panel before any text.
            int width = 0;
            for (Component row : rows) width = Math.max(width, font.width(row));
            float left = -width / 2.0F - 4;
            float right = width / 2.0F + 4;
            float top = -3;
            float bottom = (rows.length - 1) * (font.lineHeight + 5) + font.lineHeight + 3;
            VertexConsumer panel = buffers.getBuffer(GUIDES);
            panelVertex(poses, panel, left, top);
            panelVertex(poses, panel, right, top);
            panelVertex(poses, panel, left, bottom);
            panelVertex(poses, panel, left, bottom);
            panelVertex(poses, panel, right, top);
            panelVertex(poses, panel, right, bottom);
            buffers.endBatch();
            // GUIDES restores depth testing when its batch finishes. SEE_THROUGH
            // does not forcibly disable an already enabled depth test in 1.20.1.
            // Keep it disabled through the actual glyph upload, not just queuing.
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            try {
                for (int i = 0; i < rows.length; i++) {
                    font.drawInBatch(rows[i], -font.width(rows[i]) / 2.0F, i * (font.lineHeight + 5),
                            0xFFFFFFFF, false, poses.last().pose(), buffers,
                            Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
                }
                buffers.endBatch();
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            }
        } finally {
            poses.popPose();
        }
    }
}
