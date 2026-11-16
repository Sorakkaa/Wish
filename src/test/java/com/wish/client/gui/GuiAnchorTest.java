package com.wish.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiAnchorTest {

    @Test
    void resolvesHorizontalAndVerticalAnchors() {
        assertEquals(10, GuiAnchor.TOP_LEFT.resolveX(10, 100, 800));
        assertEquals(20, GuiAnchor.TOP_LEFT.resolveY(20, 40, 600));

        assertEquals(360, GuiAnchor.CENTER.resolveX(10, 100, 800));
        assertEquals(300, GuiAnchor.CENTER.resolveY(20, 40, 600));

        assertEquals(690, GuiAnchor.BOTTOM_RIGHT.resolveX(10, 100, 800));
        assertEquals(540, GuiAnchor.BOTTOM_RIGHT.resolveY(20, 40, 600));

        assertEquals(350, GuiAnchor.TOP_CENTER.resolveX(0, 100, 800));
        assertEquals(0, GuiAnchor.TOP_CENTER.resolveY(0, 40, 600));
        assertEquals(700, GuiAnchor.CENTER_RIGHT.resolveX(0, 100, 800));
        assertEquals(560, GuiAnchor.BOTTOM_LEFT.resolveY(0, 40, 600));
    }
}
