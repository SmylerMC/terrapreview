package net.smyler.terrapreview;

import java.awt.image.BufferedImage;

/**
 * A preview of the world, renderable as a pyramid of image tiles.
 */
public interface Preview {
    /**
     * Renders the tile at the given zoom level and tile coordinates as an image.
     *
     * <p>At zoom 0, each tile covers one full chunk. Each additional zoom level halves the
     * number of blocks per tile and doubles the number of pixels per block, so tiles always
     * render at {@code EarthPreview.TILE_SIZE} pixels and show a quarter of the area per axis
     * of the previous level. Negative zoom levels cover multiple chunks per tile.
     *
     * @param zoom the zoom level, 0 being one chunk per tile
     * @param tileX the x coordinate of the tile, in tile units at the given zoom
     * @param tileZ the z coordinate of the tile, in tile units at the given zoom
     * @return the image tile
     */
    BufferedImage renderTile(int zoom, int tileX, int tileZ);

    /**
     * Returns the geographic coordinates of the given Minecraft block position.
     *
     * @param x the block x coordinate
     * @param z the block z coordinate
     * @return a two-element array of the longitude and latitude, in degrees
     * @throws UnsupportedOperationException when the preview has no geographic mapping
     */
    default double[] blockToGeo(double x, double z) {
        throw new UnsupportedOperationException("This preview has no geographic mapping");
    }

    /**
     * Returns the Minecraft block position of the given geographic coordinates.
     *
     * @param longitude the longitude, in degrees
     * @param latitude the latitude, in degrees
     * @return a two-element array of the block x and z coordinates
     * @throws UnsupportedOperationException when the preview has no geographic mapping
     * @throws IllegalArgumentException when the location is outside the projection bounds
     */
    default double[] geoToBlock(double longitude, double latitude) {
        throw new UnsupportedOperationException("This preview has no geographic mapping");
    }
}
