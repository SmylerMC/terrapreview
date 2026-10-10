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

The first run installs the npm dependencies in `web-test/`, downloads a
Puppeteer-managed Chrome, and, on Linux systems where Chrome's shared libraries
are missing (common in minimal containers and sandboxes, where there is often no
root access to install packages), fetches them into `~/.cache/terrapreview/` from
the distribution's package repositories without requiring root. Node.js 22+ must
be available on the `PATH`. Subsequent runs reuse everything already set up.

The following environment variables are supported:

| Variable | Effect |
| --- | --- |
| `PUPPETEER_EXECUTABLE_PATH` | Use an existing Chrome installation instead of downloading one |
| `CHROME_LIBS_DIR` | Directory of pre-provisioned Chrome shared libraries, used instead of fetching them automatically |

## Run

```sh
./gradlew run
```

The web UI is served on `http://localhost:8080`.

On start, Terrapreview downloads the latest Minecraft client JAR from Mojang's official servers and caches it under
`terraminusminus/terrapreview/`. The block textures used to render the map tiles are loaded from this JAR. If it
cannot be downloaded, Terrapreview falls back to flat colors and logs an error.

### Command-line options

The listen port of the web server can be changed with the `--port` (or `-p`) flag:

```sh
./gradlew run --args="--port 9090"
```

The flag also accepts the `--port=9090` form. The port must be a number between 1 and 65535; the default is 8080.

The map projection can be selected with the `--projection` (or `-P`) flag:

```sh
./gradlew run --args="--projection terra++"
```

Two shorthands are recognized:

| Name | Projection |
| --- | --- |
| `bte` (default) | The Build The Earth conformal dymaxion projection Terra-- uses by default |
| `terra++` | The default Terra-- projection (scaled equirectangular) |

Any other name is resolved as a Terra-- projection type, e.g. `equirectangular`, `sinusoidal`, `web_mercator` or `bte_conformal_dymaxion`. An unknown name is rejected with an error message.

The version of the Minecraft client to load block textures from can be selected with the `--mc-version` flag:

```sh
./gradlew run --args="--mc-version 1.21.8"
```

The flag also accepts the `--mc-version=1.21.8` form. Any version published in Mojang's version manifest is
accepted, including snapshots such as `26.4-snapshot-3`. By default the latest release is used.

The initial map center can be chosen either in geographic coordinates or in Minecraft block coordinates:

```sh
./gradlew run --args="--center-lon 2.2890 --center-lat 48.8620"
./gradlew run --args="--center-x 1000 --center-z -2000"
```

The flags also accept the `--center-lon=<longitude>` form. Longitude and latitude must be valid degrees (-180/180 and -90/90); a geographic center is converted to Minecraft coordinates using the selected projection. The two forms cannot be mixed. By default the map is centered on the Trocadero, Paris.

By default Terrapreview opens your browser at the preview page when the server starts, unless stdout is not a terminal (e.g. when running under a service manager or in CI). You can force either behavior:

```sh
./gradlew run --args="--open-browser"     # always open the browser
./gradlew run --args="--no-open-browser"  # never open the browser
```

If a browser cannot be opened on your system, Terrapreview logs a warning with the URL to visit instead.

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
