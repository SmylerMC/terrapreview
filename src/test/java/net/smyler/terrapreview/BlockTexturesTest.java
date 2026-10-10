package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockTexturesTest {

    private static final int BYTE_MASK = 0xFF;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int OPAQUE_ALPHA = 0xFF000000;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int OAK_LOG_TOP_RGB = 0x6B4E26;
    private static final int WHITE_RGB = 0xFFFFFF;
    private static final int GRASS_TINT_RED = 0x7C;
    private static final int GRASS_TINT_GREEN = 0xBD;
    private static final int GRASS_TINT_BLUE = 0x6B;
    private static final int WATER_TINT_RED = 0x3F;
    private static final int WATER_TINT_GREEN = 0x76;
    private static final int WATER_TINT_BLUE = 0xE4;
    private static final int RIGHT_ANGLE = 90;
    private static final int FULL_TURN = 360;
    private static final int VARIANT_SAMPLE_SPAN = 64;
    private static final int SAMPLE_X = 123;
    private static final int SAMPLE_Z = -456;
    private static final String GRASS_BLOCK = "grass_block";
    private static final String LAVA_BLOCK = "lava";
    private static final String BRICKS_BLOCK = "bricks";
    private static final String PODZOL_BLOCK = "podzol";
    private static final String WATER_BLOCK = "water";
    private static final int PERIODICITY_SAMPLE_SPAN = 256;
    private static final int VARIANT_COUNT = 4;
    private static final int TEXTURE_SIZE = 2;
    private static final int CORNER_PIXEL = 0x123456;
    private static final long X_POSITION_SALT = 3129871L;
    private static final long Z_POSITION_SALT = 116129781L;
    private static final long SEED_SQUARE_SALT = 42317861L;
    private static final long SEED_LINE_SALT = 11L;
    private static final int DISTRIBUTION_TOLERANCE = 10;
    private static final int KEY_PRIME = 31;

    private static final String OAK_LOG_BLOCKSTATE =
            "{ \"variants\": { \"axis=y\": { \"model\": \"minecraft:block/oak_log\" } } }";

    private static final String OAK_LOG_MODEL =
            "{ \"parent\": \"minecraft:block/cube_column\", \"textures\": { \"end\": \"minecraft:block/oak_log_top\", \"side\": \"minecraft:block/oak_log\" } }";

    private static final String CUBE_COLUMN_MODEL =
            "{ \"parent\": \"minecraft:block/cube\", \"textures\": { \"down\": \"#end\", \"up\": \"#end\", \"north\": \"#side\", \"east\": \"#side\", \"south\": \"#side\", \"west\": \"#side\" } }";

    private static final String CUBE_MODEL =
            "{ \"elements\": [ { \"faces\": { \"up\": { \"texture\": \"#up\" } } } ] }";

    private static final String GRASS_BLOCKSTATE =
            "{ \"variants\": { \"\": { \"model\": \"minecraft:block/grass_block\" } } }";

    private static final String GRASS_MODEL =
            "{ \"textures\": { \"top\": \"minecraft:block/grass_block_top\" }, \"elements\": [ { \"faces\": { \"up\": { \"texture\": \"#top\" } } } ] }";

    private static final String WATER_BLOCKSTATE =
            "{ \"variants\": { \"\": { \"model\": \"minecraft:block/water\" } } }";

    private static final String WATER_MODEL = "{ \"textures\": { \"particle\": \"minecraft:block/water_still\" } }";

    private static final String LAVA_BLOCKSTATE = "{ \"variants\": { \"\": { \"model\": \"minecraft:block/lava\" } } }";

    private static final String PODZOL_BLOCKSTATE =
            "{ \"variants\": { \"snowy=false\": [ { \"model\": \"minecraft:block/podzol\" }, { \"model\": \"minecraft:block/podzol\", \"y\": 90 }, { \"model\": \"minecraft:block/podzol\", \"y\": 180 }, { \"model\": \"minecraft:block/podzol\", \"y\": 270 } ] } }";

    private static final String LAVA_MODEL = "{ \"textures\": { \"particle\": \"minecraft:block/lava_still\" } }";

    private static final String PODZOL_MODEL =
            "{ \"textures\": { \"top\": \"minecraft:block/podzol_top\" }, \"elements\": [ { \"faces\": { \"up\": { \"texture\": \"#top\" } } } ] }";

    private static final String BRICKS_BLOCKSTATE =
            "{ \"variants\": { \"\": { \"model\": \"minecraft:block/bricks\" } } }";

    private static final String BRICKS_MODEL =
            "{ \"textures\": { \"top\": \"minecraft:block/bricks_top\" }, \"elements\": [ { \"faces\": { \"up\": { \"texture\": \"#top\" } } } ] }";

    @TempDir
    Path tempDir;

    private Path jarPath;

    @BeforeEach
    void buildClientJar() throws IOException {
        this.jarPath = this.tempDir.resolve("client.jar");
        TestJarBuilder jar = new TestJarBuilder(this.jarPath);
        jar.addJson("assets/minecraft/blockstates/oak_log.json", OAK_LOG_BLOCKSTATE);
        jar.addJson("assets/minecraft/blockstates/grass_block.json", GRASS_BLOCKSTATE);
        jar.addJson("assets/minecraft/models/block/oak_log.json", OAK_LOG_MODEL);
        jar.addJson("assets/minecraft/models/block/cube_column.json", CUBE_COLUMN_MODEL);
        jar.addJson("assets/minecraft/models/block/cube.json", CUBE_MODEL);
        jar.addJson("assets/minecraft/models/block/grass_block.json", GRASS_MODEL);
        jar.addPng("assets/minecraft/textures/block/oak_log_top.png", OAK_LOG_TOP_RGB);
        jar.addPng("assets/minecraft/textures/block/grass_block_top.png", WHITE_RGB);
        jar.addJson("assets/minecraft/blockstates/water.json", WATER_BLOCKSTATE);
        jar.addJson("assets/minecraft/models/block/water.json", WATER_MODEL);
        jar.addPng("assets/minecraft/textures/block/water_still.png", WHITE_RGB);
        jar.addJson("assets/minecraft/blockstates/lava.json", LAVA_BLOCKSTATE);
        jar.addJson("assets/minecraft/models/block/lava.json", LAVA_MODEL);
        jar.addCornerPng("assets/minecraft/textures/block/lava_still.png", CORNER_PIXEL);
        jar.addJson("assets/minecraft/blockstates/podzol.json", PODZOL_BLOCKSTATE);
        jar.addJson("assets/minecraft/models/block/podzol.json", PODZOL_MODEL);
        jar.addCornerPng("assets/minecraft/textures/block/podzol_top.png", CORNER_PIXEL);
        jar.addJson("assets/minecraft/blockstates/bricks.json", BRICKS_BLOCKSTATE);
        jar.addJson("assets/minecraft/models/block/bricks.json", BRICKS_MODEL);
        jar.addCornerPng("assets/minecraft/textures/block/bricks_top.png", CORNER_PIXEL);
        jar.build();
    }

    @Test
    void loadsTheTopFaceTextureOfABlockWithAParentModelChain() throws IOException {
        BlockTextures textures = new BlockTextures(this.jarPath);
        Optional<BufferedImage> texture = textures.topTexture("oak_log", 0, 0);
        assertTrue(texture.isPresent());
        assertEquals(OAK_LOG_TOP_RGB, texture.get().getRGB(0, 0) & RGB_MASK);
    }

    @Test
    void tintsGrassTextures() throws IOException {
        BlockTextures textures = new BlockTextures(this.jarPath);
        BufferedImage texture = textures.topTexture(GRASS_BLOCK, 0, 0).orElseThrow();
        int rgb = texture.getRGB(0, 0);
        assertEquals(GRASS_TINT_RED, rgb >> RED_SHIFT & BYTE_MASK);
        assertEquals(GRASS_TINT_GREEN, rgb >> GREEN_SHIFT & BYTE_MASK);
        assertEquals(GRASS_TINT_BLUE, rgb & BYTE_MASK);
    }

    @Test
    void returnsEmptyForUnknownBlocks() throws IOException {
        BlockTextures textures = new BlockTextures(this.jarPath);
        assertTrue(textures.topTexture("stone", 0, 0).isEmpty());
    }

    @Test
    void resolvesTopTextureNamesThroughModelParents() throws IOException {
        try (ZipFile jar = new ZipFile(this.jarPath.toFile())) {
            assertEquals(
                    Optional.of("minecraft:block/oak_log_top"),
                    BlockTextures.topTextureName(jar, "minecraft:block/oak_log"));
        }
    }

    @Test
    void loadsTheParticleTextureOfBlocksWithoutElements() throws IOException {
        BlockTextures textures = new BlockTextures(this.jarPath);
        Optional<BufferedImage> texture = textures.topTexture("water", 0, 0);
        assertTrue(texture.isPresent());
        int rgb = texture.get().getRGB(0, 0);
        assertEquals(WATER_TINT_RED, rgb >> RED_SHIFT & BYTE_MASK);
        assertEquals(WATER_TINT_GREEN, rgb >> GREEN_SHIFT & BYTE_MASK);
        assertEquals(WATER_TINT_BLUE, rgb & BYTE_MASK);
    }

    @Test
    void cropsAnimatedTextureStripsToTheirFirstFrame() {
        BufferedImage strip = new BufferedImage(1, 2, BufferedImage.TYPE_INT_ARGB);
        strip.setRGB(0, 0, OPAQUE_ALPHA);
        BufferedImage frame = BlockTextures.firstFrame(strip);
        assertEquals(1, frame.getWidth());
        assertEquals(1, frame.getHeight());
        BufferedImage square = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        assertEquals(square, BlockTextures.firstFrame(square));
    }

    @Test
    void rotatesImagesByQuarterTurns() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 0, OPAQUE_ALPHA | OAK_LOG_TOP_RGB);
        BufferedImage rotated = BlockTextures.rotate(image, RIGHT_ANGLE);
        assertEquals(OPAQUE_ALPHA | OAK_LOG_TOP_RGB, rotated.getRGB(1, 1));
        assertEquals(image, BlockTextures.rotate(image, FULL_TURN));
    }

    @Test
    void picksDifferentTextureVariantsAtDifferentPositions() throws IOException {
        BlockTextures textures = new BlockTextures(this.jarPath);
        java.util.Set<Long> variants = new java.util.HashSet<>();
        for (int x = 0; x < VARIANT_SAMPLE_SPAN; x++) {
            for (int z = 0; z < VARIANT_SAMPLE_SPAN; z++) {
                variants.add(textureKey(textures.topTexture(PODZOL_BLOCK, x, z).orElseThrow()));
            }
        }
        assertTrue(variants.size() > 1, "expected several texture variants across positions");
    }

    @Test
    void singleModelBlocksNeverRotate() throws IOException {
        BlockTextures textures = new BlockTextures(this.jarPath);
        BufferedImage reference = textures.topTexture(BRICKS_BLOCK, 0, 0).orElseThrow();
        for (int x = 0; x < VARIANT_SAMPLE_SPAN; x++) {
            for (int z = 0; z < VARIANT_SAMPLE_SPAN; z++) {
                assertEquals(
                        textureKey(reference),
                        textureKey(textures.topTexture(BRICKS_BLOCK, x, z).orElseThrow()));
            }
        }
    }

    @Test
    void liquidBlocksNeverRotate() throws IOException {
        BlockTextures textures = new BlockTextures(this.jarPath);
        BufferedImage lavaReference = textures.topTexture(LAVA_BLOCK, 0, 0).orElseThrow();
        BufferedImage waterReference = textures.topTexture(WATER_BLOCK, 0, 0).orElseThrow();
        for (int x = 0; x < VARIANT_SAMPLE_SPAN; x++) {
            for (int z = 0; z < VARIANT_SAMPLE_SPAN; z++) {
                assertEquals(
                        textureKey(lavaReference),
                        textureKey(textures.topTexture(LAVA_BLOCK, x, z).orElseThrow()));
                assertEquals(
                        textureKey(waterReference),
                        textureKey(textures.topTexture(WATER_BLOCK, x, z).orElseThrow()));
            }
        }
    }

    @Test
    void returnsTheSameVariantForTheSamePosition() throws IOException {
        BlockTextures textures = new BlockTextures(this.jarPath);
        BufferedImage first =
                textures.topTexture(GRASS_BLOCK, SAMPLE_X, SAMPLE_Z).orElseThrow();
        BufferedImage second =
                textures.topTexture(GRASS_BLOCK, SAMPLE_X, SAMPLE_Z).orElseThrow();
        assertEquals(first, second);
    }

    @Test
    void positionSeedMatchesTheVanillaGetSeedFormula() {
        long i = (long) SAMPLE_X * X_POSITION_SALT ^ (long) SAMPLE_Z * Z_POSITION_SALT;
        long expected = i * i * SEED_SQUARE_SALT + i * SEED_LINE_SALT;
        assertEquals(expected, BlockTextures.positionSeed(SAMPLE_X, SAMPLE_Z));
        assertEquals(0L, BlockTextures.positionSeed(0, 0));
    }

    @Test
    void textureVariantsAreNotPeriodicAcrossTheMap() throws IOException {
        BlockTextures textures = new BlockTextures(this.jarPath);
        java.util.Map<Long, Integer> variantCounts = new java.util.HashMap<>();
        for (int x = 0; x < PERIODICITY_SAMPLE_SPAN; x++) {
            for (int z = 0; z < PERIODICITY_SAMPLE_SPAN; z++) {
                BufferedImage texture = textures.topTexture(PODZOL_BLOCK, x, z).orElseThrow();
                variantCounts.merge(textureKey(texture), 1, Integer::sum);
            }
        }
        long totalCells = (long) PERIODICITY_SAMPLE_SPAN * PERIODICITY_SAMPLE_SPAN;
        assertEquals(VARIANT_COUNT, variantCounts.size());
        for (int count : variantCounts.values()) {
            assertTrue(
                    Math.abs(count - totalCells / VARIANT_COUNT) < totalCells / DISTRIBUTION_TOLERANCE,
                    "variant distribution is skewed: " + variantCounts);
        }
    }

    private static long textureKey(BufferedImage texture) {
        long key = 0;
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                key = key * KEY_PRIME + texture.getRGB(x, y);
            }
        }
        return key;
    }

    @Test
    void tintedImagesMultiplyPixelColors() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(BYTE_MASK, BYTE_MASK, BYTE_MASK).getRGB());
        BufferedImage tinted =
                BlockTextures.tinted(image, new Color(GRASS_TINT_RED, GRASS_TINT_GREEN, GRASS_TINT_BLUE));
        assertEquals(new Color(GRASS_TINT_RED, GRASS_TINT_GREEN, GRASS_TINT_BLUE).getRGB(), tinted.getRGB(0, 0));
    }

    private static final class TestJarBuilder {
        private final Path path;
        private final Map<String, byte[]> entries = new HashMap<>();

        TestJarBuilder(Path path) {
            this.path = path;
        }

        void addJson(String name, String json) {
            this.entries.put(name, json.getBytes(StandardCharsets.UTF_8));
        }

        void addPng(String name, int rgb) throws IOException {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, OPAQUE_ALPHA | rgb);
            this.addImage(name, image);
        }

        void addCornerPng(String name, int rgb) throws IOException {
            BufferedImage image = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, OPAQUE_ALPHA | rgb);
            this.addImage(name, image);
        }

        private void addImage(String name, BufferedImage image) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            this.entries.put(name, out.toByteArray());
        }

        void build() throws IOException {
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(this.path))) {
                for (var entry : this.entries.entrySet()) {
                    out.putNextEntry(new ZipEntry(entry.getKey()));
                    out.write(entry.getValue());
                    out.closeEntry();
                }
            }
        }
    }
}
