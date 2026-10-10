# terrapreview

A development tool for the [terraminusminus](https://github.com/SmylerMC/terraminusminus) Minecraft world generation engine. It will serve a simple web UI visualizing generated worlds and live-reload on configuration changes.

## Requirements

- Java 25+
- Node.js 22+ (only needed to run the in-browser tests)

## Build

```sh
./gradlew build
```

## Testing

Terrapreview has two test layers, both run by `./gradlew check` (and therefore by
`./gradlew build`):

| Layer | Framework | Location | Run on its own |
| --- | --- | --- | --- |
| Java unit tests | [JUnit 6](https://junit.org/junit6/) | `src/test/java/` | `./gradlew test` |
| In-browser web UI tests | [Mocha](https://mochajs.org/) + [Puppeteer](https://pptr.dev/) | `web-test/` | `./gradlew browserTest` |

### In-browser tests

The web UI (the Leaflet map page under `src/main/resources/web/`) is tested in a
real Chrome. The suite in `web-test/` starts a small Node harness server that:

- serves the actual page served in production, with the placeholders substituted,
- mocks the `/tiles/z/x/y.png` chunk tile endpoint, and
- mocks the `/events` server-sent-event stream, letting tests simulate
  configuration-triggered preview reloads.

The tests cover tile rendering, absence of client-side script errors, and
versioned tile reloads over server-sent events.

The first run installs the npm dependencies in `web-test/` and downloads a
Puppeteer-managed Chrome automatically; Node.js 22+ must be available on the
`PATH`. Subsequent runs reuse them.

The following environment variables are supported:

| Variable | Effect |
| --- | --- |
| `PUPPETEER_EXECUTABLE_PATH` | Use an existing Chrome installation instead of downloading one |
| `CHROME_LIBS_DIR` | Extra directory added to `LD_LIBRARY_PATH` when launching Chrome, for systems where Chrome's shared libraries are not in the default paths |

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
