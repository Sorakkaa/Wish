package com.wish.client.color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorUtilsTest {

    @Test
    void hsbToRgbProducesPrimaryAndAchromaticColors() {
        assertEquals(0xFFFF0000, ColorUtils.hsbToRgb(0.0f, 1.0f, 1.0f));
        assertEquals(0xFF00FF00, ColorUtils.hsbToRgb(1.0f / 3.0f, 1.0f, 1.0f));
        assertEquals(0xFF0000FF, ColorUtils.hsbToRgb(2.0f / 3.0f, 1.0f, 1.0f));
        assertEquals(0xFFFFFFFF, ColorUtils.hsbToRgb(0.25f, 0.0f, 1.0f));
        assertEquals(0xFF000000, ColorUtils.hsbToRgb(0.5f, 1.0f, 0.0f));
    }

    @Test
    void hsbToRgbClampsOutOfRangeSaturationAndBrightness() {
        assertEquals(0xFFFF0000, ColorUtils.hsbToRgb(0.0f, 2.5f, 1.5f));
        assertEquals(0xFFFFFFFF, ColorUtils.hsbToRgb(0.0f, -1.0f, 2.0f));
        assertEquals(0xFF000000, ColorUtils.hsbToRgb(0.0f, 1.0f, -0.5f));
    }

    @Test
    void rgbAndHsbRoundTripPreservesColor() {
        float[] scratch = new float[3];
        ColorUtils.rgbIntToHsb(0x9D00FF, scratch);
        int reconstructed = ColorUtils.hsbToRgb(scratch[0], scratch[1], scratch[2]) & 0xFFFFFF;
        assertEquals(0x9D00FF, reconstructed);
    }

    @Test
    void parseHexRgbAndFormatHexRgbHandleValidAndInvalidInputs() {
        assertEquals(0x9D00FF, ColorUtils.parseHexRgb(" #9d00ff ", -1));
        assertEquals(0x12ABCD, ColorUtils.parseHexRgb("12abcd", -1));
        assertEquals(42, ColorUtils.parseHexRgb(null, 42));
        assertEquals(42, ColorUtils.parseHexRgb("#12345", 42));
        assertEquals(42, ColorUtils.parseHexRgb("#12345G", 42));
        assertEquals("#1234EF", ColorUtils.formatHexRgb(0xAA1234EF));
    }

    @Test
    void colorWheelPolarMathRoundTripsCleanly() {
        float radius = 40.0f;
        assertEquals(0.0f, ColorUtils.wheelOffsetToSaturation(0.0f, 0.0f, radius), 1e-4f);
        assertEquals(1.0f, ColorUtils.wheelOffsetToSaturation(60.0f, 60.0f, radius), 1e-4f);
        assertEquals(0.0f, ColorUtils.wheelOffsetToHue(25.0f, 0.0f), 1e-4f);
        assertEquals(0.25f, ColorUtils.wheelOffsetToHue(0.0f, 25.0f), 1e-4f);
        assertEquals(0.5f, ColorUtils.wheelOffsetToHue(-25.0f, 0.0f), 1e-4f);
        float hue = 0.83f;
        float sat = 0.65f;
        float dx = ColorUtils.hsbToWheelX(hue, sat, radius);
        float dy = ColorUtils.hsbToWheelY(hue, sat, radius);
        assertEquals(hue, ColorUtils.wheelOffsetToHue(dx, dy), 1e-3f);
        assertEquals(sat, ColorUtils.wheelOffsetToSaturation(dx, dy, radius), 1e-3f);
    }
}
