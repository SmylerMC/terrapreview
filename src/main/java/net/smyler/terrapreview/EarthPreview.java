package net.smyler.terrapreview;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.http.Http;

/**
 * Generates a grid of earth chunks around a geographic location and renders it as an image.
 */
public final class EarthPreview implements Preview {
    /**
     * The number of chunks along each axis of the generated grid.
     */
    public static final int GRID_SIZE = 8;

    /**
     * The side length of a chunk, in blocks.
     */
    private static final int CHUNK_SIZE = 16;

    /**
     * The number of bits to shift a block coordinate to get its chunk coordinate.
     */
    private static final int CHUNK_SHIFT = 4;

    /**
     * The number of image pixels used to render a single block.
     */
    public static final int PIXELS_PER_BLOCK = 16;

    /**
     * The default longitude of the grid center: the Trocadero, Paris.
     */
    public static final double DEFAULT_LONGITUDE = 2.2890;

    /**
     * The default latitude of the grid center: the Trocadero, Paris.
     */
    public static final double DEFAULT_LATITUDE = 48.8620;

    /**
     * The color used for water blocks.
     */
    private static final Color WATER_COLOR = new Color(0x2A66C9);

    /**
     * The color used for blocks without a known color.
     */
    private static final Color UNKNOWN_COLOR = new Color(0x00FF00);

    /**
     * The color used for the background, as if it was made of grass blocks.
     */
    private static final Color EMPTY_COLOR = new Color(0x3E7D1F);

    /**
     * A hard-coded color per known surface block path.
     */
    static final Map<String, Color> BLOCK_COLORS = Map.ofEntries(
            Map.entry("oak_log", new Color(0x6B4E26)),
            Map.entry("spruce_log", new Color(0x3B2710)),
            Map.entry("bricks", new Color(0x96604A)),
            Map.entry("gray_concrete", new Color(0x4A4A4A)),
            Map.entry("stone", new Color(0x7F7F7F)),
            Map.entry("dirt_path", new Color(0x907944)),
            Map.entry("grass_block", new Color(0x5FA837)),
            Map.entry("dirt", new Color(0x8B5A2B)),
            Map.entry("sand", new Color(0xDBD3A0)),
            Map.entry("gravel", new Color(0x807B72)),
            Map.entry("snow_block", new Color(0xF9FBFC)));

    /**
     * How long generated chunks stay in the cache after their last access.
     */
    private static final long CACHE_EXPIRY_MINUTES = 5L;

    private final EarthGeneratorSettings settings;
    private final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;

    /**
     * Creates a new previewer using the default Terra++ generator settings.
     */
    public EarthPreview() {
        this(EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS));
    }

    /**
     * Creates a new previewer using the given generator settings.
     *
     * @param settings the earth generator settings to use
     */
    public EarthPreview(EarthGeneratorSettings settings) {
        this.settings = settings;
        Http.configChanged();
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(settings));
    }

    /**
     * Generates a grid of chunks around the given location and renders it as an image.
     *
     * @param longitude the longitude of the grid center
     * @param latitude the latitude of the grid center
     * @return the image representation of the chunk grid
     */
    @Override
    public BufferedImage render(double longitude, double latitude) {
        GeographicProjection projection = this.settings.projection();
        double[] worldPos;
        try {
            worldPos = projection.fromGeo(longitude, latitude);
        } catch (OutOfProjectionBoundsException e) {
            throw new IllegalArgumentException("Location is outside the projection bounds", e);
        }
        int centerChunkX = (int) Math.round(worldPos[0]) >> CHUNK_SHIFT;
        int centerChunkZ = (int) Math.round(worldPos[1]) >> CHUNK_SHIFT;
        int half = GRID_SIZE / 2;
        CachedChunkData[][] chunks = new CachedChunkData[GRID_SIZE][GRID_SIZE];
        for (int cx = 0; cx < GRID_SIZE; cx++) {
            for (int cz = 0; cz < GRID_SIZE; cz++) {
                ChunkPos pos = new ChunkPos(centerChunkX - half + cx, centerChunkZ - half + cz);
                chunks[cx][cz] = this.cache.getUnchecked(pos).join();
            }
        }
        return this.toImage(chunks);
    }

    private BufferedImage toImage(CachedChunkData[][] chunks) {
        int blocks = GRID_SIZE * CHUNK_SIZE;
        BufferedImage image =
                new BufferedImage(blocks * PIXELS_PER_BLOCK, blocks * PIXELS_PER_BLOCK, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int z = 0; z < blocks; z++) {
                for (int x = 0; x < blocks; x++) {
                    CachedChunkData chunk = chunks[x / CHUNK_SIZE][z / CHUNK_SIZE];
                    graphics.setColor(colorOf(chunk, x % CHUNK_SIZE, z % CHUNK_SIZE));
                    graphics.fillRect(x * PIXELS_PER_BLOCK, z * PIXELS_PER_BLOCK, PIXELS_PER_BLOCK, PIXELS_PER_BLOCK);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static Color colorOf(CachedChunkData chunk, int x, int z) {
        if (chunk.groundHeight(x, z) < chunk.surfaceHeight(x, z)) {
            return WATER_COLOR;
        }
        String block = blockPath(chunk, x, z);
        if (block == null) {
            return EMPTY_COLOR;
        }
        return BLOCK_COLORS.getOrDefault(block, UNKNOWN_COLOR);
    }

    private static String blockPath(CachedChunkData chunk, int x, int z) {
        var state = chunk.surfaceBlock(x, z);
        return state == null ? null : state.getBlock().path;
    }
}
