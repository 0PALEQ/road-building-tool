package com.cookiecraftmods.roadbuilder.client.ui;

public final class UIStyle {
    public static final int BACKGROUND_SCRIM = 0x66000000;
    public static final int BACKGROUND_FRAME = 0xEE242424;
    public static final int BACKGROUND_PANEL = 0xEE1B1B1B;
    public static final int BACKGROUND_PANEL_RAISED = 0xF02A2A2A;
    public static final int BACKGROUND_WORKSPACE = 0xFF222222;
    public static final int BACKGROUND_STATUS = 0xE0171717;

    public static final int CONTROL_BACKGROUND = 0xFF303030;
    public static final int CONTROL_HOVERED = 0xFF464646;
    public static final int CONTROL_SELECTED = 0xFF606060;
    public static final int CONTROL_DANGER = 0xFF9E3030;
    public static final int CONTROL_DISABLED = 0xFF202020;
    public static final int CONTROL_GHOST = 0x00181818;

    public static final int BORDER_FRAME = 0xFF686868;
    public static final int BORDER_PANEL = 0xFF555555;
    public static final int BORDER_PANEL_HIGHLIGHT = 0xFF707070;
    public static final int BORDER_CONTROL = 0xFF5A5A5A;
    public static final int BORDER_CONTROL_DISABLED = 0xFF383838;
    public static final int BORDER_HOVERED = 0xFFB8B8B8;
    public static final int BORDER_PREVIEW = 0xFF808080;
    public static final int BORDER_HIGH_CONTRAST = 0xFFFFFFFF;
    public static final int BORDER_SHADOW = 0xB0000000;

    public static final int TEXT_PRIMARY = 0xFFF4F4F4;
    public static final int TEXT_SECONDARY = 0xFFAAAAAA;
    public static final int TEXT_SUBTITLE = 0xFFBDBDBD;
    public static final int TEXT_DISABLED = 0xFF747474;
    public static final int TEXT_INVERSE = 0xFF181818;
    public static final int HIGHLIGHT = 0xFFD0D0D0;
    public static final int HIGHLIGHT_STRONG = 0xFFE0E0E0;

    public static final int OVERLAY_ROW_HOVERED = 0x80505050;
    public static final int OVERLAY_COMPONENT_DIVIDER = 0x55404040;
    public static final int OVERLAY_DRAG_GHOST = 0xDD202020;
    public static final int OVERLAY_DISABLED = 0x660D0D0D;
    public static final int SHADOW_DARK = 0x88000000;
    public static final int SHADOW_LIGHT = 0x88FFFFFF;

    public static final int PREVIEW_BLOCK_FALLBACK = 0xFF5E5E5E;
    public static final int PREVIEW_ASPHALT = 0xFF3C3C3C;
    public static final int PREVIEW_ASPHALT_LIGHT = 0xFF505050;
    public static final int PREVIEW_ASPHALT_DARK = 0xFF323232;
    public static final int PREVIEW_SIDEWALK = 0xFF8F8F8F;
    public static final int PREVIEW_SIDEWALK_LIGHT = 0xFFBCBCBC;
    public static final int PREVIEW_SIDEWALK_DARK = 0xFF626262;
    public static final int PREVIEW_ARROW_SPACING = 200;

    private UIStyle() {
    }

    public static int lighten(int color, int amount) {
        int alpha = color & 0xFF000000;
        int red = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int green = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int blue = Math.min(255, (color & 0xFF) + amount);
        return alpha | red << 16 | green << 8 | blue;
    }

    public static int darken(int color, int amount) {
        int alpha = color & 0xFF000000;
        int red = Math.max(0, ((color >> 16) & 0xFF) - amount);
        int green = Math.max(0, ((color >> 8) & 0xFF) - amount);
        int blue = Math.max(0, (color & 0xFF) - amount);
        return alpha | red << 16 | green << 8 | blue;
    }

    public static int withAlpha(int color, int alpha) {
        return alpha << 24 | color & 0x00FFFFFF;
    }

    public static int grayscale(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        return color & 0xFF000000
                | luminance << 16
                | luminance << 8
                | luminance;
    }
}
