package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ShadedTextureTest {

    private static final int PIXELS = 16;

    private static final Color GRASS_GREEN = new Color(0x60, 0x90, 0x40);

    private static final Color DIRT_BROWN = new Color(0x90, 0x60, 0x30);

    private static final int KEY_PRIME = 31;

    private static final double SHADED_BRIGHTNESS = 0.7D;

    private static final int FULLY_TRANSPARENT = 0x00000000;

    private static final int HALF_TRANSPARENT = 0x80404040;

    private static final int HALF_ALPHA = 0x80;

    private static final int ALPHA_SHIFT = 24;

    private static final int OPAQUE_BLUE = 0xFF4080C0;

    @Test
    void transparentPixelsStayTransparentWhenShaded() {
        EarthPreview preview = new EarthPreview();
        BufferedImage texture = new BufferedImage(PIXELS, PIXELS, BufferedImage.TYPE_INT_ARGB);
        texture.setRGB(0, 0, FULLY_TRANSPARENT);
        texture.setRGB(1, 0, HALF_TRANSPARENT);
        BufferedImage shaded = shaded(preview, texture, SHADED_BRIGHTNESS, 1D);
        assertEquals(FULLY_TRANSPARENT, shaded.getRGB(0, 0), "a fully transparent pixel must stay fully transparent");
        assertEquals(HALF_ALPHA, shaded.getRGB(1, 0) >>> ALPHA_SHIFT, "a half transparent pixel must keep its alpha");
    }

    @Test
    void flatBlocksKeepTheirExactTexture() {
        EarthPreview preview = new EarthPreview();
        BufferedImage texture = new BufferedImage(PIXELS, PIXELS, BufferedImage.TYPE_INT_ARGB);
        texture.setRGB(0, 0, OPAQUE_BLUE);
        BufferedImage shaded = shaded(preview, texture, 1D, 1D);
        assertEquals(OPAQUE_BLUE, shaded.getRGB(0, 0), "a flat block must keep its texture untouched");
    }

    @Test
    void differentBlocksWithTheSameHeightDifferencesAreShadedSeparately() {
        // Two block types with the same height difference must not share a shaded texture,
        // otherwise the first shaded block's texture is reused for the second.
        EarthPreview preview = new EarthPreview();
        BufferedImage grass = solidTexture(GRASS_GREEN);
        BufferedImage dirt = solidTexture(DIRT_BROWN);
        BufferedImage shadedGrass = shaded(preview, grass, SHADED_BRIGHTNESS, 1D);
        BufferedImage shadedDirt = shaded(preview, dirt, SHADED_BRIGHTNESS, 1D);
        assertNotEquals(pixelKey(shadedGrass), pixelKey(shadedDirt), "each block type must be shaded separately");
        assertNotEquals(pixelKey(grass), pixelKey(shadedGrass), "the shading must change the texture");
    }

    private static BufferedImage shaded(
            EarthPreview preview, BufferedImage texture, double northWest, double northEast) {
        return shadedTextureOf(preview, texture, northWest, northEast);
    }

    private static BufferedImage shadedTextureOf(
            EarthPreview preview, BufferedImage texture, double northWest, double northEast) {
        try {
            var method = EarthPreview.class.getDeclaredMethod(
                    "shadedTexture", BufferedImage.class, double.class, double.class, double.class, double.class);
            method.setAccessible(true);
            return (BufferedImage) method.invoke(preview, texture, northWest, northEast, 1D, 1D);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke shadedTexture", e);
        }
    }

    private static BufferedImage solidTexture(Color color) {
        BufferedImage image = new BufferedImage(PIXELS, PIXELS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, PIXELS, PIXELS);
        graphics.dispose();
        return image;
    }

    private static long pixelKey(BufferedImage image) {
        long key = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                key = key * KEY_PRIME + image.getRGB(x, y);
            }
        }
        return key;
    }
}
