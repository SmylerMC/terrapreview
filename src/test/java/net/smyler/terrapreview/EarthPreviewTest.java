package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class EarthPreviewTest {

    private static final int BYTE_MASK = 0xFF;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int DEPTH_ONE = 1;
    private static final int DEPTH_TWO = 2;
    private static final int DEPTH_EIGHT = 8;
    private static final int DEPTH_SIXTY_FOUR = 64;
    private static final int DEPTH_EXTREME = 1024;
    private static final int VISIBLE_CHANNEL_DIFFERENCE = 8;
    private static final int DEPTH_THREE = 3;
    private static final double LUMINANCE_RED_WEIGHT = 0.2126D;
    private static final double LUMINANCE_GREEN_WEIGHT = 0.7152D;
    private static final double LUMINANCE_BLUE_WEIGHT = 0.0722D;

    private static final Color WATER = new Color(0x2A66C9);

    @Test
    void waterAtDepthZeroIsUnchanged() {
        Color shallow = EarthPreview.waterDepthColor(WATER, 0);
        assertEquals(WATER.getRGB() & RGB_MASK, shallow.getRGB() & RGB_MASK);
    }

    @Test
    void consecutiveShallowDepthsAreClearlyDistinguishable() {
        Color depthOne = EarthPreview.waterDepthColor(WATER, DEPTH_ONE);
        Color depthTwo = EarthPreview.waterDepthColor(WATER, DEPTH_TWO);
        assertTrue(
                channelDistance(depthOne, depthTwo) >= VISIBLE_CHANNEL_DIFFERENCE,
                "depth 1 and depth 2 should be clearly different");
        Color depthThree = EarthPreview.waterDepthColor(WATER, DEPTH_THREE);
        assertTrue(
                channelDistance(depthTwo, depthThree) >= VISIBLE_CHANNEL_DIFFERENCE,
                "depth 2 and depth 3 should be clearly different");
    }

    @Test
    void deeperWaterIsDarker() {
        Color shallow = EarthPreview.waterDepthColor(WATER, DEPTH_ONE);
        Color deeper = EarthPreview.waterDepthColor(WATER, DEPTH_EIGHT);
        Color deepest = EarthPreview.waterDepthColor(WATER, DEPTH_SIXTY_FOUR);
        assertTrue(luminance(deeper) < luminance(shallow), "deeper water should be darker");
        assertTrue(luminance(deepest) < luminance(deeper), "deepest water should be darkest");
    }

    @Test
    void waterDarkeningSaturatesAtTheMaximumDepth() {
        assertEquals(
                EarthPreview.waterDepthColor(WATER, DEPTH_SIXTY_FOUR).getRGB() & RGB_MASK,
                EarthPreview.waterDepthColor(WATER, DEPTH_EXTREME).getRGB() & RGB_MASK);
    }

    @Test
    void darkenedWaterStaysWithinTheChannelRange() {
        Color overlay = EarthPreview.waterDepthColor(WATER, DEPTH_SIXTY_FOUR);
        assertTrue(overlay.getRed() >= 0 && overlay.getRed() <= BYTE_MASK);
        assertTrue(overlay.getGreen() >= 0 && overlay.getGreen() <= BYTE_MASK);
        assertTrue(overlay.getBlue() >= 0 && overlay.getBlue() <= BYTE_MASK);
    }

    private static int channelDistance(Color first, Color second) {
        return Math.abs(first.getRed() - second.getRed())
                + Math.abs(first.getGreen() - second.getGreen())
                + Math.abs(first.getBlue() - second.getBlue());
    }

    private static double luminance(Color color) {
        return LUMINANCE_RED_WEIGHT * color.getRed()
                + LUMINANCE_GREEN_WEIGHT * color.getGreen()
                + LUMINANCE_BLUE_WEIGHT * color.getBlue();
    }
}
