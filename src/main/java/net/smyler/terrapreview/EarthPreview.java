package net.smyler.terrapreview;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;
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
     * The color used for water blocks without a known texture.
     */
    private static final Color WATER_COLOR = new Color(0x2A66C9);

    /**
     * The color used for blocks without a known texture.
     */
    private static final Color UNKNOWN_COLOR = new Color(0x00FF00);

    /**
     * The color used for the background without a known grass texture, as if it was made of grass blocks.
     */
    private static final Color EMPTY_COLOR = new Color(0x3E7D1F);

    /**
     * The block used to render the background, as if the world was made of grass blocks.
     */
    private static final String GRASS_BLOCK = "grass_block";

    /**
     * The block used to render water.
     */
    private static final String WATER_BLOCK = "water";

    /**
     * The color deep water is darkened towards, as vanilla water looks darker the deeper it is.
     */
    private static final Color DEEP_WATER_COLOR = new Color(0x1A2F6B);

    /**
     * The darkest blend of the depth gradient, reached in deep water.
     */
    private static final float MAX_DEPTH_ALPHA = 0.7F;

    /**
     * The water depth, in blocks, at which half of the darkening is reached; light attenuates exponentially in water,
     * so most of the visible change happens over the first few blocks.
     */
    private static final double DEPTH_HALF_LIFE = 2D;

    /**
     * Blends the deep water color over another color for the given water depth.
     *
     * <p>The blend follows exponential attenuation, like light in water: half of the darkening is reached at
     * {@link #DEPTH_HALF_LIFE} blocks and it saturates at {@link #MAX_DEPTH_ALPHA} in deep water. This keeps
     * consecutive shallow depths clearly distinguishable while deep water stays uniformly dark.
     *
     * @param base the color to darken, typically the water texture color
     * @param depth the water depth, in blocks, zero or more
     * @return the darkened color
     */
    static Color waterDepthColor(Color base, int depth) {
        double alpha = MAX_DEPTH_ALPHA * (1D - Math.exp(-Math.max(depth, 0) / DEPTH_HALF_LIFE));
        int red = Math.round((float) (base.getRed() + alpha * (DEEP_WATER_COLOR.getRed() - base.getRed())));
        int green = Math.round((float) (base.getGreen() + alpha * (DEEP_WATER_COLOR.getGreen() - base.getGreen())));
        int blue = Math.round((float) (base.getBlue() + alpha * (DEEP_WATER_COLOR.getBlue() - base.getBlue())));
        return new Color(red, green, blue);
    }

    /**
     * How long generated chunks stay in the cache after their last access.
     */
    private static final long CACHE_EXPIRY_MINUTES = 5L;

    private final EarthGeneratorSettings settings;

    private final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;

    private final BlockTextures textures;

    private final java.util.Map<Integer, BufferedImage> deepWaterTextures = new java.util.HashMap<>();

    /**
     * Creates a new previewer using the default Terra++ generator settings and no block textures.
     */
    public EarthPreview() {
        this(EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS), null);
    }

    /**
     * Creates a new previewer using the given generator settings and block textures.
     *
     * @param settings the earth generator settings to use
     * @param textures the block textures to render with, or {@code null} to render flat colors
     */
    public EarthPreview(EarthGeneratorSettings settings, BlockTextures textures) {
        this.settings = settings;
        this.textures = textures;
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
                    this.drawBlock(
                            graphics, chunk, blockX, blockZ, x * pixelsPerBlock, z * pixelsPerBlock, pixelsPerBlock);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void drawBlock(Graphics2D graphics, CachedChunkData chunk, int blockX, int blockZ, int x, int y, int size) {
        int inChunkX = blockX & CHUNK_MASK;
        int inChunkZ = blockZ & CHUNK_MASK;
        int surfaceHeight = chunk.surfaceHeight(inChunkX, inChunkZ);
        int groundHeight = chunk.groundHeight(inChunkX, inChunkZ);
        boolean water = groundHeight < surfaceHeight;
        String block;
        Color fallback;
        if (water) {
            block = WATER_BLOCK;
            fallback = WATER_COLOR;
        } else {
            var state = chunk.surfaceBlock(inChunkX, inChunkZ);
            block = state == null ? GRASS_BLOCK : state.getBlock().path;
            fallback = state == null ? EMPTY_COLOR : UNKNOWN_COLOR;
        }
        Optional<BufferedImage> texture =
                this.textures == null ? Optional.empty() : this.textures.topTexture(block, blockX, blockZ);
        if (texture.isPresent()) {
            graphics.drawImage(texture.get(), x, y, size, size, null);
        } else {
            graphics.setColor(fallback);
            graphics.fillRect(x, y, size, size);
        }
        if (water) {
            int depth = surfaceHeight - groundHeight;
            if (texture.isPresent()) {
                graphics.drawImage(this.deepWaterTexture(texture.get(), depth), x, y, size, size, null);
            } else {
                graphics.setColor(waterDepthColor(fallback, depth));
                graphics.fillRect(x, y, size, size);
            }
        }
    }

    private BufferedImage deepWaterTexture(BufferedImage texture, int depth) {
        if (depth == 0) {
            return texture;
        }
        return this.deepWaterTextures.computeIfAbsent(depth, missing -> {
            BufferedImage darkened = new BufferedImage(texture.getWidth(), texture.getHeight(), texture.getType());
            for (int z = 0; z < texture.getHeight(); z++) {
                for (int x = 0; x < texture.getWidth(); x++) {
                    darkened.setRGB(
                            x,
                            z,
                            waterDepthColor(new Color(texture.getRGB(x, z)), missing)
                                    .getRGB());
                }
            }
            return darkened;
        });
    }
}
