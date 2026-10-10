package net.smyler.terrapreview;

import java.awt.image.BufferedImage;

/**
 * A preview of a chunk grid, renderable as an image.
 */
public interface Preview {
    /**
     * Renders the preview around the given location as an image.
     *
     * @param longitude the longitude of the grid center
     * @param latitude the latitude of the grid center
     * @return the image representation of the chunk grid
     */
    BufferedImage render(double longitude, double latitude);
}
