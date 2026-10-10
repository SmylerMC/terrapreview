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
 * Generates earth chunks and renders them as image tiles, one tile per chunk.
 */
public final class EarthPreview implements Preview {
    /**
     * The side length of a chunk, in blocks.
     */
    public static final int CHUNK_SIZE = 16;

    /**
     * The number of bits to shift a block coordinate to get its chunk coordinate.
     */
    private static final int CHUNK_SHIFT = 4;

    /**
     * The bitmask of the position of a block within its chunk.
     */
    private static final int CHUNK_MASK = CHUNK_SIZE - 1;

    /**
     * The number of image pixels used to render a single block.
     */
    public static final int PIXELS_PER_BLOCK = 16;

    /**
     * The side length of a chunk tile, in pixels.
     */
    public static final int TILE_SIZE = CHUNK_SIZE * PIXELS_PER_BLOCK;

    /**
     * The default longitude of the initial map center: the Trocadero, Paris.
     */
    public static final double DEFAULT_LONGITUDE = 2.2890;

    /**
     * The default latitude of the initial map center: the Trocadero, Paris.
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
     * Returns the chunk coordinates of the default map center.
     *
     * @return a two-element array of the center chunk coordinates, x then z
     */
    public double[] defaultCenterChunk() {
        return this.geoToChunk(DEFAULT_LONGITUDE, DEFAULT_LATITUDE);
    }

    /**
     * Returns the chunk coordinates of the map center for the given geographic location.
     *
     * @param longitude the longitude of the center, in degrees
     * @param latitude the latitude of the center, in degrees
     * @return a two-element array of the center chunk coordinates, x then z
     * @throws IllegalArgumentException when the location is outside the projection bounds
     */
    public double[] centerChunk(double longitude, double latitude) {
        return this.geoToChunk(longitude, latitude);
    }

    /**
     * Returns the chunk coordinates of the map center for the given Minecraft block coordinates.
     *
     * @param x the block x coordinate of the center
     * @param z the block z coordinate of the center
     * @return a two-element array of the center chunk coordinates, x then z
     */
    public static double[] centerChunkOfBlock(double x, double z) {
        return new double[] {Math.floor(x / CHUNK_SIZE), Math.floor(z / CHUNK_SIZE)};
    }

    private double[] geoToChunk(double longitude, double latitude) {
        GeographicProjection projection = this.settings.projection();
        double[] worldPos;
        try {
            worldPos = projection.fromGeo(longitude, latitude);
        } catch (OutOfProjectionBoundsException e) {
            throw new IllegalArgumentException("Location is outside the projection bounds", e);
        }
        return new double[] {Math.floor(worldPos[0] / CHUNK_SIZE), Math.floor(worldPos[1] / CHUNK_SIZE)};
    }

    /**
     * Renders the tile at the given zoom level and tile coordinates as an image.
     *
     * @param zoom the zoom level, 0 being one chunk per tile
     * @param tileX the x coordinate of the tile, in tile units at the given zoom
     * @param tileZ the z coordinate of the tile, in tile units at the given zoom
     * @return the image tile
     */
    @Override
    public BufferedImage renderTile(int zoom, int tileX, int tileZ) {
        int scale = 1 << Math.abs(zoom);
        int blocksPerTile = zoom >= 0 ? CHUNK_SIZE / scale : CHUNK_SIZE * scale;
        int pixelsPerBlock = zoom >= 0 ? PIXELS_PER_BLOCK * scale : PIXELS_PER_BLOCK / scale;
        BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int z = 0; z < blocksPerTile; z++) {
                for (int x = 0; x < blocksPerTile; x++) {
                    int blockX = tileX * blocksPerTile + x;
                    int blockZ = tileZ * blocksPerTile + z;
                    int chunkX = blockX >> CHUNK_SHIFT;
                    int chunkZ = blockZ >> CHUNK_SHIFT;
                    CachedChunkData chunk = this.cache
                            .getUnchecked(new ChunkPos(chunkX, chunkZ))
                            .join();
                    graphics.setColor(colorOf(chunk, blockX & CHUNK_MASK, blockZ & CHUNK_MASK));
                    graphics.fillRect(x * pixelsPerBlock, z * pixelsPerBlock, pixelsPerBlock, pixelsPerBlock);
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
