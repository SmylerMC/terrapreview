package net.smyler.terrapreview;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.http.Http;

/**
 * Entry point of the TerraPreview application.
 */
public final class TerraPreview {

    private TerraPreview() {}

    /**
     * The greeting message printed by this application.
     */
    public static final String GREETING = "Hello, TerraPreview!";

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
    private static final double DEFAULT_LONGITUDE = 2.2890;

    /**
     * The default latitude of the grid center: the Trocadero, Paris.
     */
    private static final double DEFAULT_LATITUDE = 48.8620;

    /**
     * How long generated chunks stay in the cache after their last access.
     */
    private static final long CACHE_EXPIRY_MINUTES = 5L;

    /**
     * Returns the greeting message of this application.
     *
     * @return the greeting message
     */
    public static String greeting() {
        return GREETING;
    }

    /**
     * Generates a grid of chunks around the given location and prints it as ASCII art.
     *
     * @param args the command line arguments: optionally the longitude and latitude of the center
     * @throws Exception if the chunks cannot be generated
     */
    public static void main(String[] args) throws Exception {
        double longitude = args.length >= 1 ? Double.parseDouble(args[0]) : DEFAULT_LONGITUDE;
        double latitude = args.length >= 2 ? Double.parseDouble(args[1]) : DEFAULT_LATITUDE;

        Http.configChanged();

        EarthGeneratorSettings settings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
        GeographicProjection projection = settings.projection();
        double[] worldPos = projection.fromGeo(longitude, latitude);
        int centerChunkX = (int) Math.round(worldPos[0]) >> CHUNK_SHIFT;
        int centerChunkZ = (int) Math.round(worldPos[1]) >> CHUNK_SHIFT;
        int half = GRID_SIZE / 2;

        System.out.printf(
                "Generating a %dx%d chunk grid around (%.4f, %.4f)...%n", GRID_SIZE, GRID_SIZE, longitude, latitude);

        LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache = CacheBuilder.newBuilder()
                .expireAfterAccess(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(settings));
        CachedChunkData[][] chunks = new CachedChunkData[GRID_SIZE][GRID_SIZE];
        for (int cx = 0; cx < GRID_SIZE; cx++) {
            for (int cz = 0; cz < GRID_SIZE; cz++) {
                ChunkPos pos = new ChunkPos(centerChunkX - half + cx, centerChunkZ - half + cz);
                chunks[cx][cz] = cache.getUnchecked(pos).join();
            }
        }

        printAsciiArt(chunks);
    }

    private static void printAsciiArt(CachedChunkData[][] chunks) {
        int size = GRID_SIZE * CHUNK_SIZE;
        System.out.println();
        System.out.printf("Grid of %dx%d chunks (%d x %d blocks), x ->, z v:%n", GRID_SIZE, GRID_SIZE, size, size);
        System.out.println();
        for (int z = 0; z < size; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < size; x++) {
                CachedChunkData chunk = chunks[x / CHUNK_SIZE][z / CHUNK_SIZE];
                row.append(toAsciiChar(chunk, x % CHUNK_SIZE, z % CHUNK_SIZE));
            }
            System.out.println(row);
        }
        System.out.println();
        System.out.println("Legend: ~ water  T tree  # bricks  ▓ concrete/stone  = road/path");
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
                    return '▓';
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
