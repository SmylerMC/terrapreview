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

    /**
     * The number of extra blocks sampled around a tile, for elevation contours and shading, so the lines continue
     * seamlessly across tile boundaries and zones stay closed.
     */
    private static final int MARGIN = 1;

    /**
     * The size of the sampled height grid, with a one block margin on each side of the tile.
     */
    private static final int EDGE = MARGIN * 2;

    private static final int HASH_PRIME = 31;

    private static final double BRIGHTNESS_QUANTUM = 1000D;

    private static final double HALF = 0.5D;

    private static final double FLAT_BRIGHTNESS = 1D;

    private final EarthGeneratorSettings settings;

    private final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;

    private final BlockTextures textures;

    private final java.util.Map<Integer, BufferedImage> deepWaterTextures = new java.util.HashMap<>();

    private final java.util.Map<ShadedTextureKey, BufferedImage> shadedTextures = new java.util.HashMap<>();

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

    /**
     * Returns the geographic coordinates of the given Minecraft block position.
     *
     * @param x the block x coordinate
     * @param z the block z coordinate
     * @return a two-element array of the longitude and latitude, in degrees
     * @throws IllegalArgumentException when the position is outside the projection bounds
     */
    @Override
    public double[] blockToGeo(double x, double z) {
        GeographicProjection projection = this.settings.projection();
        try {
            return projection.toGeo(x, z);
        } catch (OutOfProjectionBoundsException e) {
            throw new IllegalArgumentException("Location is outside the projection bounds", e);
        }
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
        int[][] heights = new int[blocksPerTile + EDGE][blocksPerTile + EDGE];
        boolean[][] water = new boolean[blocksPerTile + EDGE][blocksPerTile + EDGE];
        try {
            for (int z = 0; z < blocksPerTile + EDGE; z++) {
                for (int x = 0; x < blocksPerTile + EDGE; x++) {
                    int blockX = tileX * blocksPerTile + x - MARGIN;
                    int blockZ = tileZ * blocksPerTile + z - MARGIN;
                    int chunkX = blockX >> CHUNK_SHIFT;
                    int chunkZ = blockZ >> CHUNK_SHIFT;
                    CachedChunkData chunk = this.cache
                            .getUnchecked(new ChunkPos(chunkX, chunkZ))
                            .join();
                    heights[x][z] = chunk.surfaceHeight(blockX & CHUNK_MASK, blockZ & CHUNK_MASK);
                    water[x][z] = chunk.groundHeight(blockX & CHUNK_MASK, blockZ & CHUNK_MASK) < heights[x][z];
                    if (x >= MARGIN && z >= MARGIN && x < blocksPerTile + MARGIN && z < blocksPerTile + MARGIN) {
                        this.drawBlock(
                                graphics,
                                chunk,
                                new TerrainContext(
                                        heights[x][z],
                                        heights[x][z - 1],
                                        heights[x - 1][z],
                                        heights[x - 1][z - 1],
                                        blockX,
                                        blockZ),
                                (x - MARGIN) * pixelsPerBlock,
                                (z - MARGIN) * pixelsPerBlock,
                                pixelsPerBlock);
                    }
                }
            }
            graphics.translate(-MARGIN * pixelsPerBlock, -MARGIN * pixelsPerBlock);
            Contours.draw(graphics, heights, water, pixelsPerBlock, contourVisibility(pixelsPerBlock));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * Returns the strength of the contour lines for a tile rendering blocks at the given size: full strength at the
     * native block size, fading to nothing as blocks shrink to a few pixels at low zoom levels, where the lines
     * would dominate the tile.
     *
     * @param pixelsPerBlock the size of a block in the tile, in pixels
     * @return the strength of the lines, from 0 for invisible to 1 for full strength
     */
    static float contourVisibility(int pixelsPerBlock) {
        return Math.min(1F, pixelsPerBlock / (float) PIXELS_PER_BLOCK);
    }

    private void drawBlock(Graphics2D graphics, CachedChunkData chunk, TerrainContext terrain, int x, int y, int size) {
        int blockX = terrain.blockX();
        int blockZ = terrain.blockZ();
        int surfaceHeight = terrain.surfaceHeight();
        int inChunkX = blockX & CHUNK_MASK;
        int inChunkZ = blockZ & CHUNK_MASK;
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
        if (water) {
            Color depthColor = texture.isPresent() ? null : waterDepthColor(fallback, surfaceHeight - groundHeight);
            if (texture.isPresent()) {
                graphics.drawImage(texture.get(), x, y, size, size, null);
                graphics.drawImage(
                        this.deepWaterTexture(texture.get(), surfaceHeight - groundHeight), x, y, size, size, null);
            } else {
                graphics.setColor(depthColor);
                graphics.fillRect(x, y, size, size);
            }
            return;
        }
        double northWest = Shading.cornerBrightness(
                surfaceHeight, terrain.northHeight(), terrain.westHeight(), terrain.northWestHeight());
        double northEast = Shading.cornerBrightness(surfaceHeight, terrain.northHeight(), surfaceHeight, surfaceHeight);
        double southWest = Shading.cornerBrightness(surfaceHeight, surfaceHeight, terrain.westHeight(), surfaceHeight);
        double southEast = Shading.cornerBrightness(surfaceHeight, surfaceHeight, surfaceHeight, surfaceHeight);
        if (texture.isPresent()) {
            BufferedImage shaded = this.shadedTexture(texture.get(), northWest, northEast, southWest, southEast);
            graphics.drawImage(shaded, x, y, size, size, null);
        } else {
            Color shadedFallback = Shading.shade(fallback, northWest, northEast, southWest, southEast);
            graphics.setColor(shadedFallback);
            graphics.fillRect(x, y, size, size);
        }
    }

    private BufferedImage shadedTexture(
            BufferedImage texture, double northWest, double northEast, double southWest, double southEast) {
        if (northWest == FLAT_BRIGHTNESS
                && northEast == FLAT_BRIGHTNESS
                && southWest == FLAT_BRIGHTNESS
                && southEast == FLAT_BRIGHTNESS) {
            return texture;
        }
        return this.shadedTextures.computeIfAbsent(
                ShadedTextureKey.of(texture, northWest, northEast, southWest, southEast), missing -> {
                    BufferedImage shaded =
                            new BufferedImage(texture.getWidth(), texture.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int z = 0; z < texture.getHeight(); z++) {
                        double vertical = (z + HALF) / texture.getHeight();
                        for (int x = 0; x < texture.getWidth(); x++) {
                            double horizontal = (x + HALF) / texture.getWidth();
                            Color base = new Color(texture.getRGB(x, z), true);
                            Color shade = Shading.shade(
                                    base,
                                    missing.northWest(),
                                    missing.northEast(),
                                    missing.southWest(),
                                    missing.southEast(),
                                    horizontal,
                                    vertical);
                            shaded.setRGB(x, z, shade.getRGB());
                        }
                    }
                    return shaded;
                });
    }

    /**
     * The texture and corner brightnesses of a block, as a shading cache key.
     *
     * <p>The texture is compared by identity, as each block variant has its own texture instance, and the
     * brightnesses are quantized so that visually identical gradients share one shaded copy.
     *
     * @param texture the texture of the block being shaded
     * @param northWest the brightness of the north west corner
     * @param northEast the brightness of the north east corner
     * @param southWest the brightness of the south west corner
     * @param southEast the brightness of the south east corner
     */
    private record ShadedTextureKey(
            BufferedImage texture, double northWest, double northEast, double southWest, double southEast) {

        private static ShadedTextureKey of(
                BufferedImage texture, double northWest, double northEast, double southWest, double southEast) {
            return new ShadedTextureKey(
                    texture, quantize(northWest), quantize(northEast), quantize(southWest), quantize(southEast));
        }

        private static double quantize(double brightness) {
            return Math.round(brightness * BRIGHTNESS_QUANTUM) / BRIGHTNESS_QUANTUM;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ShadedTextureKey key
                    && key.texture == this.texture
                    && key.northWest == this.northWest
                    && key.northEast == this.northEast
                    && key.southWest == this.southWest
                    && key.southEast == this.southEast;
        }

        @Override
        public int hashCode() {
            int hash = System.identityHashCode(this.texture);
            hash = hash * HASH_PRIME + Double.hashCode(this.northWest);
            hash = hash * HASH_PRIME + Double.hashCode(this.northEast);
            hash = hash * HASH_PRIME + Double.hashCode(this.southWest);
            hash = hash * HASH_PRIME + Double.hashCode(this.southEast);
            return hash;
        }
    }

    /**
     * The terrain context of a block being drawn: its position and the heights used for its shading.
     *
     * @param surfaceHeight the surface height of the block
     * @param northHeight the surface height of the block to its north
     * @param westHeight the surface height of the block to its west
     * @param blockX the x coordinate of the block
     * @param blockZ the z coordinate of the block
     */
    /**
     * The terrain context of a block being drawn: its position and the heights of its neighborhood.
     *
     * @param surfaceHeight the surface height of the block
     * @param northHeight the surface height of the block to its north
     * @param westHeight the surface height of the block to its west
     * @param northWestHeight the surface height of the block to its north west
     * @param blockX the x coordinate of the block
     * @param blockZ the z coordinate of the block
     */
    private record TerrainContext(
            int surfaceHeight, int northHeight, int westHeight, int northWestHeight, int blockX, int blockZ) {}

    private BufferedImage deepWaterTexture(BufferedImage texture, int depth) {
        if (depth == 0) {
            return texture;
        }
        return this.deepWaterTextures.computeIfAbsent(depth, missing -> {
            BufferedImage darkened =
                    new BufferedImage(texture.getWidth(), texture.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int z = 0; z < texture.getHeight(); z++) {
                for (int x = 0; x < texture.getWidth(); x++) {
                    darkened.setRGB(
                            x,
                            z,
                            waterDepthColor(new Color(texture.getRGB(x, z), true), missing)
                                    .getRGB());
                }
            }
            return darkened;
        });
    }
}
