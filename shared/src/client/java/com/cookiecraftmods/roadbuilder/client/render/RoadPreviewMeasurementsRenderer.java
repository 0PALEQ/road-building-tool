package com.cookiecraftmods.roadbuilder.client.render;

import com.cookiecraftmods.roadbuilder.client.tool.RawRoadPreview;
import com.cookiecraftmods.roadbuilder.client.tool.StraightRoadToolController;
import com.cookiecraftmods.roadbuilder.client.tool.StraightRoadToolController.AttachmentAngle;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.client.ui.RoadGuiDrawing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Locale;

public final class RoadPreviewMeasurementsRenderer {
    private static final int GUIDE_COLOR = 0xFFF2FAFF;
    private static final int PANEL_COLOR = 0xF20A0F14;
    private static final int ANGLE_STEPS = 32;
    private PreviewFrame frame;

    public void capture(RoadWorldRenderFrame context, StraightRoadToolController tool) {
        frame = null;
        var preview = tool.rawPreview();
        if (preview.isEmpty() || context.matrixStack() == null) {
            return;
        }

        // Capture the same transform as the preview before the world pass is composited.
        frame = new PreviewFrame(preview.get(), tool.previewAttachmentAngles(),
                context.cameraPosition(), context.projection());
    }

    public void renderOverlay(GuiGraphics graphics) {
        PreviewFrame current = frame;
        frame = null;
        if (current != null) {
            current.render(graphics);
        }
    }

    private record PreviewFrame(
            RawRoadPreview preview, List<AttachmentAngle> attachments, Vec3 camera, Matrix4f projection
    ) {
        void render(GuiGraphics graphics) {
            var geometry = preview.geometry();
            double length = geometry.length();

            for (AttachmentAngle attachment : attachments) {
                var road = attachment.roadDirection();
                var branch = attachment.previewDirection();
                if (road.horizontalLength() > 1.0e-9 && branch.horizontalLength() > 1.0e-9) {
                    angle(graphics, attachment.point(),
                            Math.atan2(road.z(), road.x()), Math.atan2(branch.z(), branch.x()));
                }
            }
            // Skip the free-end guide if there is already a guide at that attachment.
            if (attachments.stream().noneMatch(a -> a.point().distanceTo(geometry.end()) < 0.05)) {
                var entry = geometry.sample(0.0).tangent();
                var exit = geometry.sample(length).tangent();
                if (entry.horizontalLength() > 1.0e-9 && exit.horizontalLength() > 1.0e-9) {
                    angle(graphics, geometry.end(),
                            Math.atan2(-entry.z(), -entry.x()), Math.atan2(exit.z(), exit.x()));
                }
            }

            RoadPoint midpoint = geometry.sample(length / 2.0).position();
            double rise = geometry.end().y() - geometry.start().y();
            double run = preview.horizontalLength();
            String grade = run > 1.0e-9 ? String.format(Locale.ROOT, "%+.1f%%", rise / run * 100.0) : "—";
            label(graphics, new RoadPoint(midpoint.x(), midpoint.y() + 2.0, midpoint.z()),
                    Component.translatable("preview.roadbuilder.length", decimal(length)),
                    Component.translatable("preview.roadbuilder.elevation", decimal(geometry.end().y()),
                            String.format(Locale.ROOT, "%+.1f", rise)),
                    Component.translatable("preview.roadbuilder.grade", grade));
        }

        private void angle(GuiGraphics graphics, RoadPoint pivot, double from, double to) {
            double sweep = Math.atan2(Math.sin(to - from), Math.cos(to - from));
            double radius = preview.width() / 2.0 + 3.0;
            HudPoint start = project(graphics, anglePoint(pivot, radius, from, 1.2));
            HudPoint previous = start;
            for (int i = 1; i <= ANGLE_STEPS; i++) {
                HudPoint next = project(graphics, anglePoint(pivot, radius, from + sweep * i / ANGLE_STEPS, 1.2));
                line(graphics, previous, next);
                previous = next;
            }
            HudPoint center = project(graphics, anglePoint(pivot, 0.0, from, 1.2));
            line(graphics, center, start);
            line(graphics, center, previous);
            label(graphics, anglePoint(pivot, radius + 2.0, from + sweep / 2.0, 2.0),
                    Component.translatable("preview.roadbuilder.angle", decimal(Math.toDegrees(Math.abs(sweep)))));
        }

        private void label(GuiGraphics graphics, RoadPoint anchor, Component... rows) {
            HudPoint point = project(graphics, anchor);
            if (point == null || point.x() < 0 || point.x() >= graphics.guiWidth()
                    || point.y() < 0 || point.y() >= graphics.guiHeight()) {
                return;
            }

            var font = Minecraft.getInstance().font;
            int width = 0;
            for (Component row : rows) {
                width = Math.max(width, font.width(row));
            }
            int lineHeight = font.lineHeight + 3;
            int x = Math.round(point.x());
            int y = Math.round(point.y()) - (rows.length * lineHeight) / 2;
            graphics.fill(x - width / 2 - 4, y - 3, x + (width + 1) / 2 + 4,
                    y + rows.length * lineHeight, PANEL_COLOR);
            for (int i = 0; i < rows.length; i++) {
                graphics.drawString(font, rows[i], x - font.width(rows[i]) / 2,
                        y + i * lineHeight, 0xFFFFFFFF, false);
            }
        }

        private HudPoint project(GuiGraphics graphics, RoadPoint point) {
            return RoadPreviewMeasurementsRenderer.project(projection, camera, point,
                    graphics.guiWidth(), graphics.guiHeight());
        }
    }

    static HudPoint project(Matrix4f projection, Vec3 camera, RoadPoint point, int width, int height) {
        var clip = projection.transform(new Vector4f(
                (float) (point.x() - camera.x), (float) (point.y() - camera.y),
                (float) (point.z() - camera.z), 1.0F));
        if (!clip.isFinite() || clip.w <= 0.0F || clip.z < -clip.w || clip.z > clip.w) {
            return null;
        }
        return new HudPoint((clip.x / clip.w + 1.0F) * width / 2.0F,
                (1.0F - clip.y / clip.w) * height / 2.0F);
    }

    private static void line(GuiGraphics graphics, HudPoint from, HudPoint to) {
        if (from == null || to == null) {
            return;
        }
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        int length = (int) Math.round(Math.hypot(dx, dy));
        if (length == 0) {
            return;
        }

        RoadGuiDrawing.line(graphics, from.x(), from.y(), (float) Math.atan2(dy, dx), length, GUIDE_COLOR);
    }

    private static RoadPoint anglePoint(RoadPoint pivot, double radius, double angle, double height) {
        return new RoadPoint(pivot.x() + radius * Math.cos(angle), pivot.y() + height,
                pivot.z() + radius * Math.sin(angle));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    record HudPoint(float x, float y) { }
}
