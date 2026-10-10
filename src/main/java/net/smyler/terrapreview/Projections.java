package net.smyler.terrapreview;

import net.buildtheearth.terraminusminus.config.GlobalParseRegistries;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;

/**
 * The map projections known to Terrapreview.
 */
public final class Projections {
    /**
     * Shorthand for the Build The Earth projection used by Terra-- by default.
     */
    public static final String BTE = "bte";

    /**
     * Shorthand for the default projection of Terra--.
     */
    public static final String TERRA_PLUS_PLUS = "terra++";

    private static final String CUSTOM_SETTINGS_TEMPLATE = """
            {
                "projection": {
                    "scale": {
                        "delegate": {"@PROJECTION@": {}},
                        "x": 100000.0,
                        "y": 100000.0
                    }
                },
                "useDefaultHeights": true,
                "useDefaultTrees": true,
                "version": 2
            }""";

    private static final String PROJECTION_PLACEHOLDER = "@PROJECTION@";

    private Projections() {}

    /**
     * Returns the generator settings using the projection of the given name.
     *
     * <p>Two shorthands are recognized: {@value BTE} for the BTE projection used by Terra-- by default
     * and {@value TERRA_PLUS_PLUS} for the default Terra-- projection. Any other name is interpreted
     * as a Terra-- projection type name, such as {@code equirectangular} or {@code bte_conformal_dymaxion}.
     *
     * @param name the projection name
     * @return generator settings using the requested projection
     * @throws IllegalArgumentException when the name is not a known projection
     */
    public static EarthGeneratorSettings settings(String name) {
        return switch (name) {
            case BTE -> EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
            case TERRA_PLUS_PLUS -> EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.DEFAULT_SETTINGS);
            default -> {
                if (!GlobalParseRegistries.PROJECTIONS.containsKey(name)) {
                    throw new IllegalArgumentException("Unknown projection: " + name);
                }
                yield EarthGeneratorSettings.parseUncached(
                        CUSTOM_SETTINGS_TEMPLATE.replace(PROJECTION_PLACEHOLDER, name));
            }
        };
    }
}
