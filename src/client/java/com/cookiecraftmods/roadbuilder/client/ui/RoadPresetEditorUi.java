package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class RoadPresetEditorUi {
    private static final String KEY = "screen.roadbuilder.preset_editor.";

    private RoadPresetEditorUi() {
    }

    static List<UiRectangle> columns(
            int x,
            int y,
            int width,
            int height,
            int count,
            int gap
    ) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        int available = Math.max(0, width - gap * (count - 1));
        int baseWidth = available / count;
        List<UiRectangle> result = new ArrayList<>(count);
        int cursor = x;
        for (int index = 0; index < count; index++) {
            int columnWidth = index == count - 1
                    ? x + width - cursor
                    : baseWidth;
            result.add(new UiRectangle(cursor, y, columnWidth, height));
            cursor += columnWidth + gap;
        }
        return result;
    }

    static Component componentLabel(RoadPresetEditorComponent component) {
        String suffix = switch (component.kind()) {
            case VEHICLE_LANE -> "lane";
            case BUS_LANE -> "bus_lane";
            case PAVEMENT -> "pavement";
            case SOLID_MARKING -> "solid_marking";
            case DASHED_MARKING -> "dashed_marking";
            case DOUBLE_CONTINUOUS_MARKING -> "double_marking";
            case SIDEWALK -> "sidewalk";
            case GRASS_MEDIAN -> "grass_median";
            case CONCRETE_MEDIAN -> "concrete_median";
        };
        return Component.translatable(KEY + suffix);
    }

    static Component directionButtonLabel(LaneTravelDirection direction) {
        return Component.literal(
                direction == LaneTravelDirection.FORWARD ? "↑  " : "↓  "
        ).append(Component.translatable(
                KEY + (direction == LaneTravelDirection.FORWARD
                        ? "forward"
                        : "backward")
        ));
    }

    static LaneTravelDirection opposite(LaneTravelDirection direction) {
        return direction == LaneTravelDirection.FORWARD
                ? LaneTravelDirection.BACKWARD
                : LaneTravelDirection.FORWARD;
    }

    static UiRectangle inset(UiRectangle rectangle, int amount) {
        int horizontal = Math.min(amount, rectangle.width() / 2);
        int vertical = Math.min(amount, rectangle.height() / 2);
        return new UiRectangle(
                rectangle.x() + horizontal,
                rectangle.y() + vertical,
                rectangle.width() - horizontal * 2,
                rectangle.height() - vertical * 2
        );
    }

    static UiRectangle union(UiRectangle first, UiRectangle second) {
        int x = Math.min(first.x(), second.x());
        int y = Math.min(first.y(), second.y());
        int right = Math.max(first.right(), second.right());
        int bottom = Math.max(first.bottom(), second.bottom());
        return new UiRectangle(x, y, right - x, bottom - y);
    }

    static boolean containedBy(
            UiRectangle container,
            UiRectangle candidate
    ) {
        return candidate.x() >= container.x()
                && candidate.y() >= container.y()
                && candidate.right() <= container.right()
                && candidate.bottom() <= container.bottom()
                && candidate.width() > 0
                && candidate.height() > 0;
    }

    static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
