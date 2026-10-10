package net.smyler.terrapreview;

/**
 * A preview of a chunk grid, renderable as ASCII art.
 */
public interface Preview {

    /**
     * Renders the preview around the given location as ASCII art.
     *
     * @param longitude the longitude of the grid center
     * @param latitude the latitude of the grid center
     * @return the ASCII art representation of the chunk grid
     */
    String render(double longitude, double latitude);
}
