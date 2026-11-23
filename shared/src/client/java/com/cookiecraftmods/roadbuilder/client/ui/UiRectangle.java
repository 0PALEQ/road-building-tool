package com.cookiecraftmods.roadbuilder.client.ui;

public record UiRectangle(int x, int y, int width, int height) {
    public UiRectangle {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height must be non-negative");
        }
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double cursorX, double cursorY) {
        return cursorX >= x && cursorX < right() && cursorY >= y && cursorY < bottom();
    }
}
