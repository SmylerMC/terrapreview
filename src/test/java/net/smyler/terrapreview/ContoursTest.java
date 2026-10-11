package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ContoursTest {

    private static final int CELL = 16;

    private static final int MARGIN = 1;

    private static final int GRID_SIZE = 3;

    private static final int ZONE_GRID_SIZE = 5;

    private static final int ZONE_EDGE = ZONE_GRID_SIZE - 1;

    private static final int RGB_MASK = 0xFFFFFF;

    private static final Color BACKGROUND = Color.WHITE;

    private static final Color LINE_COLOR = new Color(0x3B, 0x2A, 0x1A, 0x66);

    private static final float FULL_VISIBILITY = 1F;





    private static final int LOW = 5;

    private static final int MID = 6;

    private static final int HIGH = 7;

    private static final float MAX_CHANNEL = 255F;

    /**
     * The color the line has once composited over the white background.
     */
    private static final int COMPOSITED_LINE_RGB = blendOver(BACKGROUND, LINE_COLOR) & RGB_MASK;

    /**
     * The color the line has where two lines cross and the line is composited twice.
     */
    private static final int DOUBLE_COMPOSITED_LINE_RGB =
            blendOver(new Color(COMPOSITED_LINE_RGB), LINE_COLOR) & RGB_MASK;

    @Test
    void linesFadeAtLowVisibility() {
        int[][] blocks = {{LOW, LOW, LOW}, {LOW, LOW, LOW}, {LOW, MID, HIGH}};
        BufferedImage full = draw(blocks, noWater(blocks), FULL_VISIBILITY);
        BufferedImage faded = draw(blocks, noWater(blocks), 0F);
        assertTrue(linePixelCount(faded) == 0, "invisible lines must not be drawn");
        assertTrue(linePixelCount(full) > 0, "fully visible lines must be drawn");
    }

    @Test
    void waterCarriesNoContourLines() {
        // A lake between two different land heights: the shoreline heights differ, but no line may be drawn
        // on or between water blocks.
        int[][] blocks = {{LOW, LOW, HIGH}, {LOW, MID, HIGH}, {LOW, LOW, HIGH}};
        BufferedImage allLand = draw(blocks, noWater(blocks));
        boolean[][] lake = noWater(blocks);
        lake[1][1] = true;
        BufferedImage withLake = draw(blocks, lake);
        assertTrue(linePixelCount(withLake) < linePixelCount(allLand), "water must not carry contour lines");
    }

    @Test
    void flatTerrainHasNoContourLines() {
        int[][] heights = {{LOW, LOW, LOW}, {LOW, LOW, LOW}, {LOW, LOW, LOW}};
        assertEquals(0, linePixelCount(draw(heights)));
    }

    @Test
    void aHeightGapDrawsALineOnTheSharedEdge() {
        int[][] heights = {{LOW, LOW, LOW}, {LOW, MID, LOW}, {LOW, LOW, LOW}};
        BufferedImage image = draw(heights);
        assertTrue(isLine(image.getRGB(CELL, 2 * CELL) & RGB_MASK));
        assertTrue(isLine(image.getRGB(2 * CELL, CELL) & RGB_MASK));
        assertTrue((image.getRGB(0, 0) & RGB_MASK) == (BACKGROUND.getRGB() & RGB_MASK));
    }

    @Test
    void aFullSouthwardStepDrawsItsWholeEdge() {
        int[][] heights = {{LOW, MID, HIGH}, {LOW, MID, HIGH}, {LOW, MID, HIGH}};
        BufferedImage image = draw(heights);
        assertEquals(2 * GRID_SIZE * CELL, linePixelCount(image));
        for (int x = 0; x < GRID_SIZE * CELL; x++) {
            assertTrue(isLine(image.getRGB(x, CELL) & RGB_MASK));
            assertTrue(isLine(image.getRGB(x, 2 * CELL) & RGB_MASK));
        }
    }

    @Test
    void aFullEastwardStepDrawsItsWholeEdge() {
        int[][] heights = {{LOW, LOW, LOW}, {MID, MID, MID}, {HIGH, HIGH, HIGH}};
        BufferedImage image = draw(heights);
        assertEquals(2 * GRID_SIZE * CELL, linePixelCount(image));
        for (int z = 0; z < GRID_SIZE * CELL; z++) {
            assertTrue(isLine(image.getRGB(CELL, z) & RGB_MASK));
            assertTrue(isLine(image.getRGB(2 * CELL, z) & RGB_MASK));
        }
    }

    @Test
    void linesContinueAcrossTheEdgeOfTheDrawnArea() {
        // A plateau spanning several tiles: its boundary must be drawn wherever adjacent blocks differ,
        // including at the edge of the drawn area, so the zone it delimits appears closed.
        // A plateau whose west edge runs exactly along the boundary between two tiles.
        int[][] westTile = {{LOW, LOW, LOW}, {MID, MID, MID}, {MID, MID, MID}};
        int[][] eastTile = {{MID, MID, MID}, {LOW, LOW, LOW}, {LOW, LOW, LOW}};
        BufferedImage first = draw(westTile);
        BufferedImage second = draw(eastTile);
        // Both tiles must draw the edge between their first two block columns.
        assertTrue(isLine(first.getRGB(CELL, 0) & RGB_MASK));
        assertTrue(isLine(first.getRGB(CELL, 2 * CELL) & RGB_MASK));
        assertTrue(isLine(second.getRGB(CELL, 0) & RGB_MASK));
        assertTrue(isLine(second.getRGB(CELL, 2 * CELL) & RGB_MASK));
    }

    @Test
    void anEmptyGridDrawsNothing() {
        assertEquals(0, linePixelCount(draw(new int[0][0])));
    }

    /**
     * Draws the given block heights, placing them in a grid with a one block margin of low land around them.
     */
    private static BufferedImage draw(int[][] blocks) {
        return draw(blocks, noWater(blocks), FULL_VISIBILITY);
    }

    private static BufferedImage draw(int[][] blocks, boolean[][] water) {
        return draw(blocks, water, FULL_VISIBILITY);
    }

    private static BufferedImage draw(int[][] blocks, boolean[][] water, float visibility) {
        int size = blocks.length;
        if (size == 0) {
            BufferedImage empty = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D nothing = empty.createGraphics();
            Contours.draw(nothing, new int[0][0], new boolean[0][0], CELL, FULL_VISIBILITY);
            nothing.dispose();
            return empty;
        }
        int depth = blocks[0].length;
        BufferedImage image = new BufferedImage(size * CELL, depth * CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, size * CELL, depth * CELL);
        graphics.translate(-MARGIN * CELL, -MARGIN * CELL);
        Contours.draw(graphics, withMargin(blocks), withMargin(water), CELL, visibility);
        graphics.dispose();
        return image;
    }

    /**
     * Builds the water mask of the given blocks: none of them is water.
     */
    private static boolean[][] noWater(int[][] blocks) {
        return new boolean[blocks.length][blocks.length == 0 ? 0 : blocks[0].length];
    }

    private static int[][] withMargin(int[][] blocks) {
        int size = blocks.length;
        int depth = blocks[0].length;
        int[][] grid = new int[size + MARGIN][depth + MARGIN];
        for (int x = 0; x < size + MARGIN; x++) {
            for (int z = 0; z < depth + MARGIN; z++) {
                int blockX = Math.min(Math.max(x - MARGIN, 0), size - 1);
                int blockZ = Math.min(Math.max(z - MARGIN, 0), depth - 1);
                grid[x][z] = blocks[blockX][blockZ];
            }
        }
        return grid;
    }

    private static boolean[][] withMargin(boolean[][] flags) {
        int size = flags.length;
        if (size == 0) {
            return new boolean[0][0];
        }
        int depth = flags[0].length;
        boolean[][] grid = new boolean[size + MARGIN][depth + MARGIN];
        for (int x = 0; x < size + MARGIN; x++) {
            for (int z = 0; z < depth + MARGIN; z++) {
                int blockX = Math.min(Math.max(x - MARGIN, 0), size - 1);
                int blockZ = Math.min(Math.max(z - MARGIN, 0), depth - 1);
                grid[x][z] = flags[blockX][blockZ];
            }
        }
        return grid;
    }

    private static boolean isLine(int rgb) {
        return rgb == COMPOSITED_LINE_RGB || rgb == DOUBLE_COMPOSITED_LINE_RGB;
    }

    private static int linePixelCount(BufferedImage image) {
        int count = 0;
        for (int z = 0; z < image.getHeight(); z++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isLine(image.getRGB(x, z) & RGB_MASK)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int blendOver(Color base, Color overlay) {
        float alpha = overlay.getAlpha() / MAX_CHANNEL;
        int red = Math.round(base.getRed() * (1F - alpha) + overlay.getRed() * alpha);
        int green = Math.round(base.getGreen() * (1F - alpha) + overlay.getGreen() * alpha);
        int blue = Math.round(base.getBlue() * (1F - alpha) + overlay.getBlue() * alpha);
        return new Color(red, green, blue).getRGB();
    }
}
