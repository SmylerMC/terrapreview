package net.smyler.terrapreview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A fallback preview of blank white tiles, served while the Terra-- configuration is invalid.
 */
public final class UnavailablePreview implements Preview {

    @Override
    public BufferedImage renderTile(int zoom, int tileX, int tileZ) {
        BufferedImage image =
                new BufferedImage(EarthPreview.TILE_SIZE, EarthPreview.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, EarthPreview.TILE_SIZE, EarthPreview.TILE_SIZE);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
