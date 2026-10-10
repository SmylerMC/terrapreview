package net.smyler.terrapreview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * The block textures of a Minecraft client JAR, loaded from its asset files.
 *
 * <p>Each known block is mapped to the texture of the top face of its default model, resolved by following the model
 * inheritance chain, and tinted the way Minecraft renders it where applicable.
 */
public final class BlockTextures {

    private static final String ASSETS_PREFIX = "assets/minecraft/";
    private static final String BLOCKSTATES_DIRECTORY = ASSETS_PREFIX + "blockstates/";
    private static final String MODELS_DIRECTORY = ASSETS_PREFIX + "models/";
    private static final String TEXTURES_DIRECTORY = ASSETS_PREFIX + "textures/";
    private static final String JSON_SUFFIX = ".json";
    private static final String PNG_SUFFIX = ".png";
    private static final String MINECRAFT_PREFIX = "minecraft:";

    private static final String VARIANTS_FIELD = "variants";
    private static final String MULTIPART_FIELD = "multipart";
    private static final String MODEL_FIELD = "model";
    private static final String PARENT_FIELD = "parent";
    private static final String TEXTURES_FIELD = "textures";
    private static final String ELEMENTS_FIELD = "elements";
    private static final String FACES_FIELD = "faces";
    private static final String TEXTURE_FIELD = "texture";
    private static final String UP_FACE = "up";
    private static final String Y_ROTATION_FIELD = "y";
    private static final String TEXTURE_REFERENCE_PREFIX = "#";

    /**
     * The maximum number of model parents to follow when resolving a texture.
     */
    private static final int MAX_PARENT_DEPTH = 16;

    /**
     * The grass color Minecraft tints grass-like block textures with, taken from the plains biome colormap.
     */
    private static final Color GRASS_TINT = new Color(0x7CBD6B);

    /**
     * The color Minecraft tints water textures with, taken from the plains biome colormap.
     */
    private static final Color WATER_TINT = new Color(0x3F76E4);

    /**
     * The number of texture variants Minecraft randomly picks from for rotatable blocks.
     */
    private static final int VARIANT_COUNT = 4;

    private static final long X_POSITION_SALT = 3129871L;
    private static final long Z_POSITION_SALT = 116129781L;
    private static final long SEED_SQUARE_SALT = 42317861L;
    private static final long SEED_LINE_SALT = 11L;

    private static final String PARTICLE_TEXTURE = "particle";

    private static final int DEGREES_PER_TURN = 360;
    private static final int RIGHT_ANGLE = 90;

    private static final int BYTE_MASK = 0xFF;
    private static final int ALPHA_MASK = 0xFF000000;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;

    private final Map<String, BufferedImage[]> blockTopTextures = new HashMap<>();
    private final Map<String, BufferedImage> textureImages = new HashMap<>();

    /**
     * Loads the block textures of the blocks a Minecraft client JAR has models for.
     *
     * @param jarPath the path of the Minecraft client JAR to load textures from
     * @throws IOException if the JAR cannot be read or contains invalid assets
     */
    public BlockTextures(Path jarPath) throws IOException {
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            for (String block : this.blocksWithBlockstates(jar)) {
                this.loadBlock(jar, block);
            }
        }
    }

    private java.util.List<String> blocksWithBlockstates(ZipFile jar) {
        return jar.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.startsWith(BLOCKSTATES_DIRECTORY) && name.endsWith(JSON_SUFFIX))
                .map(name -> name.substring(BLOCKSTATES_DIRECTORY.length(), name.length() - JSON_SUFFIX.length()))
                .sorted()
                .toList();
    }

    private void loadBlock(ZipFile jar, String block) throws IOException {
        JsonObject blockstate = readJson(jar, BLOCKSTATES_DIRECTORY + block + JSON_SUFFIX)
                .orElseThrow(() -> new IOException("Missing blockstate file for " + block));
        java.util.List<Integer> rotations = new java.util.ArrayList<>();
        java.util.List<String> models = models(blockstate, rotations);
        if (models.isEmpty()) {
            return;
        }
        BufferedImage[] variants = new BufferedImage[models.size() > 1 ? VARIANT_COUNT : 1];
        for (int i = 0; i < models.size() && i < variants.length; i++) {
            Optional<String> texture = topTextureName(jar, models.get(i));
            if (texture.isEmpty()) {
                continue;
            }
            BufferedImage image = this.textureImage(jar, texture.get());
            if (image == null) {
                continue;
            }
            image = firstFrame(image);
            Color tint = tintFor(block);
            if (tint != null) {
                image = tinted(image, tint);
            }
            variants[i] = rotate(image, rotations.get(i));
        }
        if (variants[0] != null) {
            for (int i = 1; i < variants.length; i++) {
                if (variants[i] == null) {
                    variants[i] = rotate(variants[0], i * RIGHT_ANGLE);
                }
            }
            this.blockTopTextures.put(block, variants);
        }
    }

    /**
     * Collects the models referenced by a blockstate file, with their rotation, in declaration order.
     *
     * @param blockstate the parsed contents of a blockstate file
     * @param rotations an output list, receiving the {@code y} rotation of each collected model, in degrees
     * @return the referenced model identifiers, possibly empty
     */
    static java.util.List<String> models(JsonObject blockstate, java.util.List<Integer> rotations) {
        java.util.List<String> names = new java.util.ArrayList<>();
        JsonElement variants = blockstate.get(VARIANTS_FIELD);
        if (variants != null && variants.isJsonObject()) {
            for (JsonElement variant : variants.getAsJsonObject().asMap().values()) {
                collectModels(variant, names, rotations);
            }
        }
        JsonElement multipart = blockstate.get(MULTIPART_FIELD);
        if (multipart != null && multipart.isJsonArray()) {
            for (JsonElement part : multipart.getAsJsonArray()) {
                collectModels(part, names, rotations);
            }
        }
        return names;
    }

    private static void collectModels(
            JsonElement variant, java.util.List<String> names, java.util.List<Integer> rotations) {
        if (variant.isJsonArray()) {
            for (JsonElement element : variant.getAsJsonArray()) {
                collectModels(element, names, rotations);
            }
            return;
        }
        if (!variant.isJsonObject()) {
            return;
        }
        JsonObject object = variant.getAsJsonObject();
        JsonElement model = object.get(MODEL_FIELD);
        if (model != null && model.isJsonPrimitive()) {
            names.add(model.getAsString());
            rotations.add(
                    object.has(Y_ROTATION_FIELD) && object.get(Y_ROTATION_FIELD).isJsonPrimitive()
                            ? object.get(Y_ROTATION_FIELD).getAsInt()
                            : 0);
        }
    }

    /**
     * Resolves the texture of the top face of a model, following the model inheritance chain.
     *
     * @param jar the client JAR to read model files from
     * @param model the identifier of the model, for example {@code minecraft:block/oak_log}
     * @return the resolved texture identifier, or empty if the top face texture cannot be resolved
     * @throws IOException if a model file cannot be read
     */
    static Optional<String> topTextureName(ZipFile jar, String model) throws IOException {
        Map<String, String> textures = new HashMap<>();
        String id = model;
        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            JsonObject node = readJson(jar, modelToPath(id)).orElse(null);
            if (node == null) {
                return Optional.empty();
            }
            JsonElement declared = node.get(TEXTURES_FIELD);
            if (declared != null && declared.isJsonObject()) {
                for (var entry : declared.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            String up = topFaceTexture(node, textures);
            if (up != null) {
                return Optional.of(up);
            }
            JsonElement parent = node.get(PARENT_FIELD);
            if (parent == null || !parent.isJsonPrimitive()) {
                String particle = textures.get(PARTICLE_TEXTURE);
                return particle == null ? Optional.empty() : Optional.of(particle);
            }
            id = parent.getAsString();
        }
        return Optional.empty();
    }

    private static String topFaceTexture(JsonObject model, Map<String, String> textures) {
        JsonElement elements = model.get(ELEMENTS_FIELD);
        if (elements == null || !elements.isJsonArray()) {
            return null;
        }
        for (JsonElement element : elements.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonElement faces = element.getAsJsonObject().get(FACES_FIELD);
            if (faces == null || !faces.isJsonObject()) {
                continue;
            }
            JsonElement up = faces.getAsJsonObject().get(UP_FACE);
            if (up == null || !up.isJsonObject()) {
                continue;
            }
            JsonElement texture = up.getAsJsonObject().get(TEXTURE_FIELD);
            if (texture != null && texture.isJsonPrimitive()) {
                return resolveTextureReference(texture.getAsString(), textures);
            }
        }
        return null;
    }

    private static String resolveTextureReference(String reference, Map<String, String> textures) {
        String name = reference;
        for (int i = 0; i < textures.size() + 1 && name != null; i++) {
            if (!name.startsWith(TEXTURE_REFERENCE_PREFIX)) {
                return name;
            }
            name = textures.get(name.substring(1));
        }
        return null;
    }

    private BufferedImage textureImage(ZipFile jar, String texture) throws IOException {
        if (this.textureImages.containsKey(texture)) {
            return this.textureImages.get(texture);
        }
        ZipEntry entry = jar.getEntry(textureToPath(texture));
        if (entry == null) {
            return null;
        }
        BufferedImage image;
        try (InputStream in = jar.getInputStream(entry)) {
            image = ImageIO.read(in);
        }
        if (image == null) {
            throw new IOException("Not a valid image: " + texture);
        }
        this.textureImages.put(texture, image);
        return image;
    }

    /**
     * Tints an image by multiplying each pixel with the given color.
     *
     * @param image the image to tint
     * @param tint the color to multiply with
     * @return a new, tinted copy of the image
     */
    static BufferedImage tinted(BufferedImage image, Color tint) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                copy.setRGB(
                        x,
                        y,
                        argb & ALPHA_MASK
                                | (argb >> RED_SHIFT & BYTE_MASK) * tint.getRed() / BYTE_MASK << RED_SHIFT
                                | (argb >> GREEN_SHIFT & BYTE_MASK) * tint.getGreen() / BYTE_MASK << GREEN_SHIFT
                                | (argb & BYTE_MASK) * tint.getBlue() / BYTE_MASK);
            }
        }
        return copy;
    }

    private static Color tintFor(String block) {
        return switch (block) {
            case "grass_block" -> GRASS_TINT;
            case "water" -> WATER_TINT;
            default -> null;
        };
    }

    /**
     * Crops an animated texture strip to its first frame.
     *
     * @param image the texture image, possibly a vertical strip of animation frames
     * @return the first frame of the animation, or the image itself if it is not animated
     */
    static BufferedImage firstFrame(BufferedImage image) {
        if (image.getHeight() == image.getWidth()) {
            return image;
        }
        return image.getSubimage(0, 0, image.getWidth(), image.getWidth());
    }

    /**
     * Rotates a square image by the given multiple of 90 degrees, clockwise.
     *
     * @param image the image to rotate
     * @param degrees the rotation angle, in degrees
     * @return the rotated image, or the image itself for angles that are multiples of 360 degrees
     */
    static BufferedImage rotate(BufferedImage image, int degrees) {
        int times = Math.floorMod(degrees, DEGREES_PER_TURN) / RIGHT_ANGLE;
        if (times == 0) {
            return image;
        }
        BufferedImage rotated = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int targetX = x;
                int targetY = y;
                for (int i = 0; i < times; i++) {
                    int swap = targetX;
                    targetX = image.getWidth() - 1 - targetY;
                    targetY = swap;
                }
                rotated.setRGB(targetX, targetY, image.getRGB(x, y));
            }
        }
        return rotated;
    }

    private static String modelToPath(String model) {
        String id = model.startsWith(MINECRAFT_PREFIX) ? model.substring(MINECRAFT_PREFIX.length()) : model;
        return MODELS_DIRECTORY + id + JSON_SUFFIX;
    }

    private static String textureToPath(String texture) {
        String id = texture.startsWith(MINECRAFT_PREFIX) ? texture.substring(MINECRAFT_PREFIX.length()) : texture;
        return TEXTURES_DIRECTORY + id + PNG_SUFFIX;
    }

    private static Optional<JsonObject> readJson(ZipFile jar, String path) throws IOException {
        ZipEntry entry = jar.getEntry(path);
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream in = jar.getInputStream(entry)) {
            return Optional.of(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject());
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Failed to parse " + path, e);
        }
    }

    /**
     * Returns the top face texture of the given block, in the variant Minecraft would render at the given position.
     *
     * <p>Minecraft seeds a random generator from the block position with {@code BlockBehaviour.getSeed(BlockPos)}
     * and uses it to pick one of the block's blockstate models for blocks with several of them, such as grass; blocks
     * with a single model, such as bricks or water, always render the same way. The same seed is derived here so the
     * map looks like Minecraft's while staying stable between renders.
     *
     * @param block the path of the block identifier, for example {@code grass_block}
     * @param x the x coordinate of the block, in blocks
     * @param z the z coordinate of the block, in blocks
     * @return the texture image, or empty if the block has no known texture
     */
    @SuppressFBWarnings(
            value = "DMI_RANDOM_USED_ONLY_ONCE",
            justification = "Vanilla seeds a new RandomSource per block position")
    public Optional<BufferedImage> topTexture(String block, int x, int z) {
        BufferedImage[] variants = this.blockTopTextures.get(block);
        if (variants == null) {
            return Optional.empty();
        }
        Random random = new Random(positionSeed(x, z));
        return Optional.ofNullable(variants[random.nextInt(variants.length)]);
    }

    /**
     * Derives the random seed Minecraft uses for a block at the given position, as implemented by vanilla
     * {@code BlockBehaviour.getSeed(BlockPos)}.
     *
     * @param x the x coordinate of the block, in blocks
     * @param z the z coordinate of the block, in blocks
     * @return the position seed
     */
    static long positionSeed(int x, int z) {
        long i = x * X_POSITION_SALT ^ z * Z_POSITION_SALT;
        return i * i * SEED_SQUARE_SALT + i * SEED_LINE_SALT;
    }
}
