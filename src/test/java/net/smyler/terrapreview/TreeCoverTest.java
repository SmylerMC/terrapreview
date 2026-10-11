package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class TreeCoverTest {

    private static final int NO_TREES = 0;

    private static final int SPARSE_TREES = 40;

    private static final int DENSE_TREES = 255;

    private static final int ALPHA_MASK = 0xFF000000;

    private static final int RGB_MASK = 0xFFFFFF;

    private static final int TREE_COLOR_RGB = 0x0D330D;

    @Test
    void bareGroundHasNoOverlay() {
        assertNull(TreeCover.overlayColor(NO_TREES));
    }

    @Test
    void denseForestHasTheStrongestOverlay() {
        Color overlay = TreeCover.overlayColor(DENSE_TREES);
        assertEquals(TREE_COLOR_RGB, overlay.getRGB() & RGB_MASK);
        assertTrue(overlay.getAlpha() > 0, "a dense forest must be visible");
    }

    @Test
    void theOverlayStrengthFollowsTreeDensity() {
        Color sparse = TreeCover.overlayColor(SPARSE_TREES);
        Color dense = TreeCover.overlayColor(DENSE_TREES);
        assertTrue(sparse.getAlpha() < dense.getAlpha(), "denser tree cover must be more opaque");
    }

    @Test
    void theOverlayNeverFullyHidesTheTerrain() {
        Color dense = TreeCover.overlayColor(DENSE_TREES);
        assertTrue((dense.getRGB() & ALPHA_MASK) != ALPHA_MASK, "the overlay must stay translucent");
    }

    @Test
    void theFaintestTreeCoverIsStillVisible() {
        Color faint = TreeCover.overlayColor(1);
        assertTrue(faint != null && faint.getAlpha() > 0, "any nonzero tree cover must be visible");
        Color denser = TreeCover.overlayColor(SPARSE_TREES);
        assertTrue(faint.getAlpha() <= denser.getAlpha(), "the faintest cover is the most transparent");
    }

    @Test
    void negativeChanceIsTreatedAsBareGround() {
        assertNull(TreeCover.overlayColor(-1));
    }
}
