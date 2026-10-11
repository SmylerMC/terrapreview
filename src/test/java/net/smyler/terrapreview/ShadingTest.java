package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class ShadingTest {

    private static final int BYTE_MASK = 0xFF;

    private static final double CENTER = 0.5D;

    private static final int HEIGHT = 5;

    private static final int HIGHER = 6;

    private static final int LOWER = 4;

    private static final int DEEPER_DROP = 11;

    private static final int HUGE_DROP = 100;

    private static final int BIG_DROP = 20;

    private static final Color BASE = new Color(0x7F, 0x7F, 0x7F);

    @Test
    void flatTerrainIsUnchanged() {
        double flat = Shading.cornerBrightness(HEIGHT, HEIGHT, HEIGHT, HEIGHT);
        Color shaded = Shading.shade(BASE, flat, flat, flat, flat);
        assertEquals(BASE.getRGB(), shaded.getRGB());
    }

    @Test
    void aCornerBelowItsNeighborsIsDarkened() {
        double shadowed = Shading.cornerBrightness(HEIGHT, HIGHER, HIGHER, HIGHER);
        Color shaded = Shading.shade(BASE, shadowed, shadowed, shadowed, shadowed);
        assertTrue(shaded.getRed() < BASE.getRed(), "shadowed block should be darker");
        assertTrue(shaded.getGreen() < BASE.getGreen());
        assertTrue(shaded.getBlue() < BASE.getBlue());
    }

    @Test
    void aCornerAboveItsNeighborsIsBrightened() {
        double exposed = Shading.cornerBrightness(HIGHER, HEIGHT, HEIGHT, HEIGHT);
        Color shaded = Shading.shade(BASE, exposed, exposed, exposed, exposed);
        assertTrue(shaded.getRed() > BASE.getRed(), "exposed block should be lighter");
        assertTrue(shaded.getGreen() > BASE.getGreen());
        assertTrue(shaded.getBlue() > BASE.getBlue());
    }

    @Test
    void theDeeperTheDropTheDarkerTheCorner() {
        double shallow = Shading.cornerBrightness(HEIGHT, HIGHER, HEIGHT, HEIGHT);
        double deep = Shading.cornerBrightness(HEIGHT, DEEPER_DROP, HEIGHT, HEIGHT);
        assertTrue(deep < shallow, "deeper drop should be darker");
    }

    @Test
    void shadingSaturatesAndStaysWithinChannelBounds() {
        double cliff = Shading.cornerBrightness(0, HUGE_DROP, HUGE_DROP, HUGE_DROP);
        assertTrue(cliff > 0, "brightness should stay positive");
        Color shaded = Shading.shade(BASE, cliff, cliff, cliff, cliff);
        assertTrue(shaded.getRed() >= 0 && shaded.getRed() <= BYTE_MASK);
        assertTrue(shaded.getGreen() >= 0 && shaded.getGreen() <= BYTE_MASK);
        assertTrue(shaded.getBlue() >= 0 && shaded.getBlue() <= BYTE_MASK);
        double lessDeep = Shading.cornerBrightness(0, BIG_DROP, BIG_DROP, BIG_DROP);
        assertTrue(cliff <= lessDeep, "shading should saturate, not invert");
    }

    @Test
    void theGradientWithinABlockFollowsTheSlope() {
        // A block on the side of a ridge: its west corner catches the light, its east corner lies in the shadow.
        double west = Shading.cornerBrightness(HEIGHT, HEIGHT, LOWER, LOWER);
        double east = Shading.cornerBrightness(HEIGHT, HEIGHT, HIGHER, HIGHER);
        Color westSide = Shading.shade(BASE, west, east, west, east, 0D, CENTER);
        Color eastSide = Shading.shade(BASE, west, east, west, east, 1D, CENTER);
        assertTrue(westSide.getRed() > eastSide.getRed(), "the lit corner should be brighter than the shadowed one");
        Color middle = Shading.shade(BASE, west, east, west, east, CENTER, CENTER);
        assertTrue(
                middle.getRed() > eastSide.getRed() && middle.getRed() < westSide.getRed(),
                "brightness interpolates between the corners");
    }
}
