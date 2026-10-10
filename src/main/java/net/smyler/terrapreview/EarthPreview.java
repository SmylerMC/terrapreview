package net.smyler.terrapreview;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
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
 * Generates a grid of earth chunks around a geographic location and renders it as ASCII art.
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
     * The default longitude of the grid center: the Trocadero, Paris.
     */
    public static final double DEFAULT_LONGITUDE = 2.2890;

    /**
     * The default latitude of the grid center: the Trocadero, Paris.
     */
    public static final double DEFAULT_LATITUDE = 48.8620;

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
        this(EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS));
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
     * Generates a grid of chunks around the given location and renders it as ASCII art.
     *
     * @param longitude the longitude of the grid center
     * @param latitude the latitude of the grid center
     * @return the ASCII art representation of the chunk grid
     */
    @Override
    public String render(double longitude, double latitude) {
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

        return this.toAsciiArt(chunks, longitude, latitude);
    }

    private String toAsciiArt(CachedChunkData[][] chunks, double longitude, double latitude) {
        int size = GRID_SIZE * CHUNK_SIZE;
        StringBuilder art = new StringBuilder();
        art.append("Grid of %dx%d chunks (%d x %d blocks) at (%.4f, %.4f), x ->, z v:"
                        .formatted(GRID_SIZE, GRID_SIZE, size, size, longitude, latitude))
                .append('\n');
        for (int z = 0; z < size; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < size; x++) {
                CachedChunkData chunk = chunks[x / CHUNK_SIZE][z / CHUNK_SIZE];
                row.append(toAsciiChar(chunk, x % CHUNK_SIZE, z % CHUNK_SIZE));
            }
            art.append(row).append('\n');
        }
        art.append("\nLegend: ~ water  T tree  # bricks  \u2593 concrete/stone  = road/path");
        return art.toString();
    }

    private static char toAsciiChar(CachedChunkData chunk, int x, int z) {
        if (chunk.groundHeight(x, z) < chunk.surfaceHeight(x, z)) {
            return '~';
        }
        String block = blockPath(chunk, x, z);
        if (block != null) {
            switch (block) {
                case "oak_log", "spruce_log" -> {
                    return 'T';
                }
                case "bricks" -> {
                    return '#';
                }
                case "gray_concrete", "stone" -> {
                    return '\u2593';
                }
                case "dirt_path" -> {
                    return '=';
                }
                default -> {
                    return '%';
                }
            }
        }
        return ' ';
    }

    private static String blockPath(CachedChunkData chunk, int x, int z) {
        var state = chunk.surfaceBlock(x, z);
        return state == null ? null : state.getBlock().path;
    }
}
