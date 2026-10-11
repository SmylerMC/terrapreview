package net.smyler.terrapreview;

import java.awt.Color;

/**
 * Smooth hill shading of stepped Minecraft terrain, as if lit from the north west.
 *
 * <p>Minecraft slopes are staircases of whole blocks. Like the game's smooth lighting, the brightness is computed per
 * block corner, from the blocks meeting at that corner, and interpolated across the block face; a block whose corner
 * is lower than its neighbors is darkened there, while a corner higher than them catches the light and is slightly
 * brightened. The result is a gradient within each block that follows the direction of the slope.
 */
public final class Shading {

    /**
     * The amount a shadowed corner is darkened by, per block of height difference.
     */
    private static final double SHADOW_FACTOR = 0.13D;

    /**
     * The maximum darkening applied to a deeply shadowed corner.
     */
    private static final double MAX_SHADOW = 0.55D;

    /**
     * The amount an exposed corner is brightened by, per block of height difference.
     */
    private static final double LIGHT_FACTOR = 0.06D;

    /**
     * The maximum brightening applied to a strongly exposed corner.
     */
    private static final double MAX_LIGHT = 0.30D;

    private static final int MAX_CHANNEL = 0xFF;

    private static final double CENTER = 0.5D;

    private Shading() {}

    /**
     * Computes the brightness of a block corner, as if lit from the north west.
     *
     * @param cornerHeight the surface height of the block the corner belongs to
     * @param northHeight the surface height of the block to the north of the corner
     * @param westHeight the surface height of the block to the west of the corner
     * @param northWestHeight the surface height of the block to the north west of the corner
     * @return the brightness of the corner, 1 for a flat corner, darker when shadowed, brighter when exposed
     */
    public static double cornerBrightness(int cornerHeight, int northHeight, int westHeight, int northWestHeight) {
        int rise = Math.max(cornerHeight - northHeight, 0)
                + Math.max(cornerHeight - westHeight, 0)
                + Math.max(cornerHeight - northWestHeight, 0);
        int drop = Math.max(northHeight - cornerHeight, 0)
                + Math.max(westHeight - cornerHeight, 0)
                + Math.max(northWestHeight - cornerHeight, 0);
        return 1D + Math.min(rise * LIGHT_FACTOR, MAX_LIGHT) - Math.min(drop * SHADOW_FACTOR, MAX_SHADOW);
    }

    /**
     * Computes the shaded color of a point of a block face, interpolating the brightness of the block's corners
     * around it, as Minecraft's smooth lighting does.
     *
     * @param color the base color at that point, typically a texture pixel
     * @param northWestBrightness the brightness of the north west corner of the block
     * @param northEastBrightness the brightness of the north east corner of the block
     * @param southWestBrightness the brightness of the south west corner of the block
     * @param southEastBrightness the brightness of the south east corner of the block
     * @param horizontal the position of the point within the block, from 0 at the west edge to 1 at the east edge
     * @param vertical the position of the point within the block, from 0 at the north edge to 1 at the south edge
     * @return the shaded color
     */
    public static Color shade(
            Color color,
            double northWestBrightness,
            double northEastBrightness,
            double southWestBrightness,
            double southEastBrightness,
            double horizontal,
            double vertical) {
        double north = northWestBrightness + (northEastBrightness - northWestBrightness) * horizontal;
        double south = southWestBrightness + (southEastBrightness - southWestBrightness) * horizontal;
        double brightness = north + (south - north) * vertical;
        int red = clamp(Math.round((float) (color.getRed() * brightness)));
        int green = clamp(Math.round((float) (color.getGreen() * brightness)));
        int blue = clamp(Math.round((float) (color.getBlue() * brightness)));
        return new Color(red, green, blue, color.getAlpha());
    }

    /**
     * Computes the shaded color of a block, using the brightness of its corners averaged into a single value.
     *
     * @param color the base color of the block
     * @param northWestBrightness the brightness of the north west corner of the block
     * @param northEastBrightness the brightness of the north east corner of the block
     * @param southWestBrightness the brightness of the south west corner of the block
     * @param southEastBrightness the brightness of the south east corner of the block
     * @return the shaded color
     */
    public static Color shade(
            Color color,
            double northWestBrightness,
            double northEastBrightness,
            double southWestBrightness,
            double southEastBrightness) {
        return shade(
                color,
                northWestBrightness,
                northEastBrightness,
                southWestBrightness,
                southEastBrightness,
                CENTER,
                CENTER);
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(channel, MAX_CHANNEL));
    }
}
