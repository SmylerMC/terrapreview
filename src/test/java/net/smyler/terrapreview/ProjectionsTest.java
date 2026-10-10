package net.smyler.terrapreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.EquirectangularProjection;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.dymaxion.BTEDymaxionProjection;
import net.buildtheearth.terraminusminus.projection.transform.ProjectionTransform;
import org.junit.jupiter.api.Test;

class ProjectionsTest {
    private static final EarthGeneratorSettings BTE_DEFAULT =
            EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

    private static final EarthGeneratorSettings TERRA_DEFAULT =
            EarthGeneratorSettings.parseUncached(EarthGeneratorSettings.DEFAULT_SETTINGS);

    @Test
    void bteShorthandUsesTheBteDefaultSettings() {
        assertEquals(
                BTE_DEFAULT.projection().toString(),
                Projections.settings(Projections.BTE).projection().toString());
    }

    @Test
    void terraPlusPlusShorthandUsesTheDefaultSettings() {
        assertEquals(
                TERRA_DEFAULT.projection().toString(),
                Projections.settings(Projections.TERRA_PLUS_PLUS).projection().toString());
    }

    @Test
    void bteShorthandUsesTheBteDymaxionProjection() {
        GeographicProjection projection = Projections.settings(Projections.BTE).projection();
        assertTrue(containsProjection(projection, BTEDymaxionProjection.class));
    }

    @Test
    void terraPlusPlusShorthandUsesTheEquirectangularProjection() {
        GeographicProjection projection =
                Projections.settings(Projections.TERRA_PLUS_PLUS).projection();
        assertTrue(containsProjection(projection, EquirectangularProjection.class));
    }

    @Test
    void projectionTypeNamesAreResolvedThroughTerraminusminus() {
        GeographicProjection projection =
                Projections.settings("equirectangular").projection();
        assertTrue(containsProjection(projection, EquirectangularProjection.class));
    }

    @Test
    void unknownProjectionNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Projections.settings("not_a_projection"));
    }

    private static boolean containsProjection(GeographicProjection projection, Class<?> type) {
        while (projection != null) {
            if (type.isInstance(projection)) {
                return true;
            }
            projection = projection instanceof ProjectionTransform transform ? transform.delegate() : null;
        }
        return false;
    }
}
