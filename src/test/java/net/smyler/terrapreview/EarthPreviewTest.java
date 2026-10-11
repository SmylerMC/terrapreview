package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EarthPreviewTest {

    private static final int BYTE_MASK = 0xFF;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int DEPTH_ONE = 1;
    private static final int DEPTH_TWO = 2;
    private static final int DEPTH_EIGHT = 8;
    private static final int DEPTH_SIXTY_FOUR = 64;
    private static final int DEPTH_EXTREME = 1024;
    private static final int VISIBLE_CHANNEL_DIFFERENCE = 8;
    private static final int DEPTH_THREE = 3;
    private static final double LUMINANCE_RED_WEIGHT = 0.2126D;
    private static final double LUMINANCE_GREEN_WEIGHT = 0.7152D;
    private static final double LUMINANCE_BLUE_WEIGHT = 0.0722D;

    private static final Color WATER = new Color(0x2A66C9);

    private static final int SERIALIZED_KEY_A = 0;

    private static final int SERIALIZED_KEY_B = 1;

    private static final int SERIALIZED_KEY_COUNT = 8;

    private static final long SERIALIZED_TIMEOUT_SECONDS = 30L;

    private static final String SERIALIZED_VALUE_A = "a";

    private static final String SERIALIZED_VALUE_B = "b";

    @Test
    void waterAtDepthZeroIsUnchanged() {
        Color shallow = EarthPreview.waterDepthColor(WATER, 0);
        assertEquals(WATER.getRGB() & RGB_MASK, shallow.getRGB() & RGB_MASK);
    }

    @Test
    void consecutiveShallowDepthsAreClearlyDistinguishable() {
        Color depthOne = EarthPreview.waterDepthColor(WATER, DEPTH_ONE);
        Color depthTwo = EarthPreview.waterDepthColor(WATER, DEPTH_TWO);
        assertTrue(
                channelDistance(depthOne, depthTwo) >= VISIBLE_CHANNEL_DIFFERENCE,
                "depth 1 and depth 2 should be clearly different");
        Color depthThree = EarthPreview.waterDepthColor(WATER, DEPTH_THREE);
        assertTrue(
                channelDistance(depthTwo, depthThree) >= VISIBLE_CHANNEL_DIFFERENCE,
                "depth 2 and depth 3 should be clearly different");
    }

    @Test
    void deeperWaterIsDarker() {
        Color shallow = EarthPreview.waterDepthColor(WATER, DEPTH_ONE);
        Color deeper = EarthPreview.waterDepthColor(WATER, DEPTH_EIGHT);
        Color deepest = EarthPreview.waterDepthColor(WATER, DEPTH_SIXTY_FOUR);
        assertTrue(luminance(deeper) < luminance(shallow), "deeper water should be darker");
        assertTrue(luminance(deepest) < luminance(deeper), "deepest water should be darkest");
    }

    @Test
    void waterDarkeningSaturatesAtTheMaximumDepth() {
        assertEquals(
                EarthPreview.waterDepthColor(WATER, DEPTH_SIXTY_FOUR).getRGB() & RGB_MASK,
                EarthPreview.waterDepthColor(WATER, DEPTH_EXTREME).getRGB() & RGB_MASK);
    }

    @Test
    void darkenedWaterStaysWithinTheChannelRange() {
        Color overlay = EarthPreview.waterDepthColor(WATER, DEPTH_SIXTY_FOUR);
        assertTrue(overlay.getRed() >= 0 && overlay.getRed() <= BYTE_MASK);
        assertTrue(overlay.getGreen() >= 0 && overlay.getGreen() <= BYTE_MASK);
        assertTrue(overlay.getBlue() >= 0 && overlay.getBlue() <= BYTE_MASK);
    }

    @Test
    void serializingLoaderServesLoadedValues() throws Exception {
        LoadingCache<Integer, CompletableFuture<String>> cache = CacheBuilder.newBuilder()
                .build(new EarthPreview.SerializingCacheLoader<>(new CacheLoader<>() {
                    @Override
                    public CompletableFuture<String> load(Integer key) {
                        return CompletableFuture.completedFuture(
                                key == SERIALIZED_KEY_A ? SERIALIZED_VALUE_A : SERIALIZED_VALUE_B);
                    }
                }));
        assertEquals(SERIALIZED_VALUE_A, cache.getUnchecked(SERIALIZED_KEY_A).join());
        assertEquals(SERIALIZED_VALUE_B, cache.getUnchecked(SERIALIZED_KEY_B).join());
    }

    @Test
    void serializingLoaderNeverLoadsMoreThanOneChunkAtOnce() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        ExecutorService loadThreads = Executors.newFixedThreadPool(SERIALIZED_KEY_COUNT);
        LoadingCache<Integer, CompletableFuture<Integer>> cache = CacheBuilder.newBuilder()
                .build(new EarthPreview.SerializingCacheLoader<>(new CacheLoader<>() {
                    @Override
                    public CompletableFuture<Integer> load(Integer key) {
                        maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        return CompletableFuture.completedFuture(key)
                                .thenApplyAsync(value -> value, loadThreads)
                                .whenComplete((_, _) -> inFlight.decrementAndGet());
                    }
                }));
        ExecutorService threads = Executors.newFixedThreadPool(SERIALIZED_KEY_COUNT);
        List<Future<?>> loads = new ArrayList<>();
        try {
            for (int i = 0; i < SERIALIZED_KEY_COUNT; i++) {
                int key = i;
                loads.add(threads.submit(() -> cache.getUnchecked(key)));
            }
            for (Future<?> load : loads) {
                load.get(SERIALIZED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            assertEquals(1, maxInFlight.get(), "more than one load was in flight at once");
            for (int i = 0; i < SERIALIZED_KEY_COUNT; i++) {
                assertEquals(i, cache.getUnchecked(i).join().intValue());
            }
        } finally {
            threads.shutdownNow();
            loadThreads.shutdownNow();
        }
    }

    @Test
    void serializingLoaderDoesNotLeakItsLockWhenLoadingFails() throws Exception {
        LoadingCache<Integer, CompletableFuture<String>> cache = CacheBuilder.newBuilder()
                .build(new EarthPreview.SerializingCacheLoader<>(new CacheLoader<>() {
                    @Override
                    public CompletableFuture<String> load(Integer key) {
                        if (key == SERIALIZED_KEY_B) {
                            throw new IllegalStateException("always fails");
                        }
                        return CompletableFuture.completedFuture(SERIALIZED_VALUE_A);
                    }
                }));
        UncheckedExecutionException first =
                assertThrows(UncheckedExecutionException.class, () -> cache.getUnchecked(SERIALIZED_KEY_B));
        assertTrue(first.getCause() instanceof IllegalStateException);
        assertEquals(SERIALIZED_VALUE_A, cache.getUnchecked(SERIALIZED_KEY_A).join());
        UncheckedExecutionException second =
                assertThrows(UncheckedExecutionException.class, () -> cache.getUnchecked(SERIALIZED_KEY_B));
        assertTrue(second.getCause() instanceof IllegalStateException);
    }

    private static int channelDistance(Color first, Color second) {
        return Math.abs(first.getRed() - second.getRed())
                + Math.abs(first.getGreen() - second.getGreen())
                + Math.abs(first.getBlue() - second.getBlue());
    }

    private static double luminance(Color color) {
        return LUMINANCE_RED_WEIGHT * color.getRed()
                + LUMINANCE_GREEN_WEIGHT * color.getGreen()
                + LUMINANCE_BLUE_WEIGHT * color.getBlue();
    }
}
