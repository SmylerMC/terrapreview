package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TerraPreviewTest {

    @Test
    void greetingIsHelloTerraPreview() {
        assertEquals("Hello, TerraPreview!", TerraPreview.greeting());
    }

    @Test
    void mainDoesNotThrow() {
        TerraPreview.main(new String[0]);
    }
}
