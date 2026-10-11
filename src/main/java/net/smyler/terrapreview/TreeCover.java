package net.smyler.terrapreview;

import java.awt.Color;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;

/**
 * Tree density rendering, as a translucent dark green overlay whose strength follows the tree cover of each block.
 */
public final class TreeCover {

    /**
     * The key Terra-- stores its per-block tree cover data under in chunk data.
     */
    private static final String TREE_COVER_KEY = "tree_cover";

    /**
     * The color of dense tree cover, as seen from above: a deep forest green, dark enough to stand out against
     * the grass and tree textures beneath it.
     */
    private static final Color TREE_COLOR = new Color(0x0D, 0x33, 0x0D);

    /**
     * The maximum opacity of the tree overlay, for a block fully covered by trees.
     */
    private static final float MAX_OPACITY = 0.8F;

    /**
     * The smallest opacity drawn, for blocks with the faintest tree cover: low density zones must stay visible;
     * blocks without any tree cover are not drawn at all.
     */
    private static final float MIN_VISIBLE_OPACITY = 0.1F;

    /**
     * The value Terra--'s tree cover data gives a fully forested block: its tree chance is computed as
     * {@code ceil(density * 0.25 * 255 * 1.5)}, capped, so full density gives about 96.
     */
    private static final int FULL_COVER_CHANCE = 96;

    /**
     * The scale of the alpha channel.
     */
    private static final int ALPHA_SCALE = 0xFF;

    private TreeCover() {}

    /**
     * Computes the color of the tree cover overlay for a block.
     *
     * @param treeChance the per-block tree chance of the block, from Terra--'s tree cover data, scaled to 255
     * @return the translucent overlay color, or {@code null} when the tree cover is negligible
     */
    public static Color overlayColor(int treeChance) {
        if (treeChance <= 0) {
            return null;
        }
        float density = Math.min(treeChance, FULL_COVER_CHANCE) / (float) FULL_COVER_CHANCE;
        float opacity = Math.max(density * MAX_OPACITY, MIN_VISIBLE_OPACITY);
        return new Color(
                TREE_COLOR.getRed(), TREE_COLOR.getGreen(), TREE_COLOR.getBlue(), Math.round(opacity * ALPHA_SCALE));
    }

    /**
     * Reads the per-block tree chance of a block, from Terra--'s tree cover data.
     *
     * @param chunk the chunk the block belongs to
     * @param x the x coordinate of the block within its chunk
     * @param z the z coordinate of the block within its chunk
     * @return the tree chance of the block, scaled to 255, or 0 when the chunk carries no tree cover data
     */
    public static int treeChance(CachedChunkData chunk, int x, int z) {
        byte[] cover = chunk.getCustom(TREE_COVER_KEY);
        if (cover == null || cover.length == 0) {
            return 0;
        }
        return cover[x * EarthPreview.CHUNK_SIZE + z] & ALPHA_SCALE;
    }
}
