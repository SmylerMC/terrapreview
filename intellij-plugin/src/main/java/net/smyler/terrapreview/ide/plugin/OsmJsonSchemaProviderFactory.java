package net.smyler.terrapreview.ide.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Contributes the Terra++ {@code osm.json5} schema so that IntelliJ provides autocompletion,
 * documentation and schema-based validation for OpenStreetMap interpretation configuration files.
 */
public final class OsmJsonSchemaProviderFactory implements JsonSchemaProviderFactory {

    private static final String SCHEMA_RESOURCE = "/schemas/osm.json";
    private static final String OSM_CONFIG_FILE_NAME = "osm.json5";

    @Override
    public @NotNull List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
        return Collections.singletonList(new OsmJsonSchemaFileProvider());
    }

    private static final class OsmJsonSchemaFileProvider implements JsonSchemaFileProvider {
        @Override
        public boolean isAvailable(@NotNull VirtualFile file) {
            return OSM_CONFIG_FILE_NAME.equals(file.getName()) && TerraConfigurationFileRecognizer.isConfigurationFile(file);
        }

        @Override
        public @NotNull String getName() {
            return "Terra++ OSM configuration";
        }

        @Override
        public VirtualFile getSchemaFile() {
            return JsonSchemaProviderFactory.getResourceFile(OsmJsonSchemaProviderFactory.class, SCHEMA_RESOURCE);
        }

        @Override
        public @NotNull SchemaType getSchemaType() {
            return SchemaType.schema;
        }

        @Override
        public @NotNull JsonSchemaVersion getSchemaVersion() {
            return JsonSchemaVersion.SCHEMA_2020_12;
        }
    }
}
