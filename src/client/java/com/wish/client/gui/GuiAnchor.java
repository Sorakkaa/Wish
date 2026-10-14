package com.wish.client.gui;

public enum GuiAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    public int resolveX(int offsetX, int contentWidth, int screenWidth) {
        return switch (this) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (screenWidth - contentWidth) / 2 + offsetX;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenWidth - contentWidth - offsetX;
        };
    }

    public int resolveY(int offsetY, int contentHeight, int screenHeight) {
        return switch (this) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> offsetY;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (screenHeight - contentHeight) / 2 + offsetY;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - contentHeight - offsetY;
        };
    }
}
