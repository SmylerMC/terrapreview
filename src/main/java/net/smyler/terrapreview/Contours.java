package net.smyler.terrapreview;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Elevation contour lines for stepped Minecraft terrain, as drawn on topographic maps.
 *
 * <p>Minecraft slopes are staircases of whole blocks, so a contour line is simply drawn along the boundary between
 * two adjacent blocks of different surface heights.
 */
public final class Contours {

    /**
     * The color of the contour lines, translucent so the tiles under them remain visible.
     */
    private static final Color LINE_COLOR = new Color(0x3B, 0x2A, 0x1A, 0x66);

    /**
     * The color of the contour lines at the given visibility, translucent so the tiles under them remain visible.
     */
    private static Color lineColor(float visibility) {
        float clamped = Math.max(0F, Math.min(visibility, 1F));
        return new Color(
                LINE_COLOR.getRed(),
                LINE_COLOR.getGreen(),
                LINE_COLOR.getBlue(),
                Math.round(LINE_COLOR.getAlpha() * clamped));
    }

    /**
     * The width of the contour lines, in pixels.
     */
    private static final float LINE_WIDTH = 1.0F;

    private Contours() {}

    /**
     * Draws the contour lines of a stepped height grid on the given graphics.
     *
     * <p>A line is drawn on the edge between two horizontally adjacent grid entries whenever their heights differ,
     * except when either of them is water: water carries its depth shading instead, so it gets neither lines nor
     * shading. Each entry is the height of a block, as in {@code heights[x][z] = surfaceHeight(x, z)}; the grid is
     * expected to cover one block more than the drawn area on each side, so that lines continue across the boundary
     * of the drawn area and zones stay closed.
     *
     * @param graphics the graphics to draw the lines on
     * @param heights the surface heights of the blocks, as {@code heights[x][z]}, with a one block margin
     * @param water which blocks are water, with the same layout as the height grid
     * @param pixelsPerCell the size of a block, in pixels
     * @param visibility the strength of the lines, from 0 for invisible to 1 for full strength; tiles at low zoom
     *                   levels render blocks at only a few pixels, where full strength lines would dominate them
     */
    public static void draw(
            Graphics2D graphics, int[][] heights, boolean[][] water, double pixelsPerCell, float visibility) {
        drawEdges(
                graphics,
                heights.length == 0 ? 0 : heights[0].length,
                heights.length,
                pixelsPerCell,
                (x, z) -> !water[x][z] && heights[x + 1][z] != heights[x][z] && !water[x + 1][z],
                (x, z) -> !water[x][z] && heights[x][z + 1] != heights[x][z] && !water[x][z + 1],
                lineColor(visibility));
    }

    /**
     * Draws lines around the zones of a boolean grid on the given graphics, in the same style as the elevation
     * contour lines: a line is drawn on the edge between two horizontally adjacent grid entries whenever exactly
     * one of them is in a zone. Unlike the elevation lines, these do not fade at low zoom levels.
     *
     * @param graphics the graphics to draw the lines on
     * @param zones which blocks are in a zone, as {@code zones[x][z]}, with a one block margin
     * @param color the color of the lines
     * @param pixelsPerCell the size of a block, in pixels
     */
    public static void drawZones(Graphics2D graphics, boolean[][] zones, Color color, double pixelsPerCell) {
        drawEdges(
                graphics,
                zones.length == 0 ? 0 : zones[0].length,
                zones.length,
                pixelsPerCell,
                (x, z) -> zones[x][z] != zones[x + 1][z],
                (x, z) -> zones[x][z] != zones[x][z + 1],
                color);
    }

    private static void drawEdges(
            Graphics2D graphics,
            int cellsZ,
            int cellsX,
            double pixelsPerCell,
            java.util.function.BiPredicate<Integer, Integer> eastEdge,
            java.util.function.BiPredicate<Integer, Integer> southEdge,
            Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(LINE_WIDTH));
        if (cellsX == 0) {
            return;
        }
        for (int x = 0; x < cellsX; x++) {
            for (int z = 0; z < cellsZ; z++) {
                if (x + 1 < cellsX && eastEdge.test(x, z)) {
                    int lineX = (int) Math.round((x + 1) * pixelsPerCell);
                    graphics.drawLine(lineX, (int) Math.round(z * pixelsPerCell), lineX, (int)
                            Math.round((z + 1) * pixelsPerCell));
                }
                if (z + 1 < cellsZ && southEdge.test(x, z)) {
                    int y = (int) Math.round((z + 1) * pixelsPerCell);
                    graphics.drawLine(
                            (int) Math.round(x * pixelsPerCell), y, (int) Math.round((x + 1) * pixelsPerCell), y);
                }
            }
        }
    }
}
