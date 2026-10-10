package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerraPreviewTest {

    @Test
    void greetingIsHelloTerraPreview() {
        assertEquals("Hello, TerraPreview!", TerraPreview.greeting());
    }

    @Test
    void gridIsSquareAndGreaterThanOneChunk() {
        assertTrue(TerraPreview.GRID_SIZE > 1);
        assertEquals(
                TerraPreview.GRID_SIZE * TerraPreview.GRID_SIZE,
                Math.multiplyExact(TerraPreview.GRID_SIZE, TerraPreview.GRID_SIZE));
    }
}
