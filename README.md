# terrapreview

A development tool for the [terraminusminus](https://github.com/SmylerMC/terraminusminus) Minecraft world generation engine. It will serve a simple web UI visualizing generated worlds and live-reload on configuration changes.

## Requirements

- Java 25+

## Build

```sh
./gradlew build
```

## Test

```sh
./gradlew test
```

## Run

```sh
./gradlew run
```

## Live-reloaded configuration

Terraminusminus resolves external configuration from a config root directory (default: `./terraminusminus/config`, configurable at runtime via `Disk#setConfigRoot`). Terrapreview watches this directory and rebuilds the generator, then live-reloads the web UI, whenever any of the following files change:

| File / directory | Purpose | Consumed by |
| --- | --- | --- |
| `heights/` (any `*.json` / `*.json5` file inside) | Elevation dataset definitions (`MultiScalarDataset("heights")`) | `EarthGeneratorPipelines` |
| `tree_cover/` (any `*.json` / `*.json5` file inside) | Tree cover dataset definitions (`MultiScalarDataset("tree_cover")`) | `EarthGeneratorPipelines` |
| `osm.json5` | OpenStreetMap tag-matching rules for roads, buildings, etc. (`OSMMapper.load()`); falls back to the built-in `osm.json5` resource when absent | `EarthGeneratorPipelines` |
| `biome_overrides/` (any `*.json` / `*.json5` file inside) | Region-based biome overrides (`UserOverrideBiomeFilter`) | `EarthGeneratorPipelines` |
| `default_generator_settings.json5` (or a generator settings string chosen by the UI) | Top-level generator settings: projection, `useDefaultHeights`, `useDefaultTrees`, config `version` (`EarthGeneratorSettings.parse`) | `EarthGeneratorSettings` |

Note that the built-in defaults for these files (including `bte_generator_settings.json5` and the preconfigured `osm.json5`) live in the terraminusminus JAR resources under `net/buildtheearth/terraminusminus/`; the files above override or extend those defaults.
