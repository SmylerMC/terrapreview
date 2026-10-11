# Terrapreview IntelliJ Plugin

A proof-of-concept [IntelliJ Platform](https://plugins.jetbrains.com/docs/intellij/) plugin that embeds
the Terrapreview live world preview in the IDE, for use while editing
[Terra--](https://github.com/SmylerMC/terraminusminus) / Terra++ configuration files.

## What it does

Terra-- configuration files (the JSON and JSON5 files under `<project>/terraminusminus/config`)
open in a **split editor**, like Markdown files do: the file's text editor on one side and a live
Terrapreview map on the other. The layout can be switched between editor-only, preview-only, and
split, with the usual `TextEditorWithPreview` controls.

The preview is served by the Terrapreview web server (the same one the CLI runs) on an ephemeral
loopback port, watching `<project>/terraminusminus/config` for configuration changes: saving any
watched file re-renders the world and the map live-reloads through the same server-sent events the
browser UI uses. External edits are picked up as well, through a filesystem watcher.

If JCEF is not available, the preview half of the split editor is not created and the file opens
as a plain text editor.

## Build

The plugin is a subproject of the root build:

```sh
./gradlew :intellij-plugin:build
```

The distributable plugin is `intellij-plugin/build/libs/*.jar` (or use
`./gradlew :intellij-plugin:runIde` to try it in a sandboxed IDEA Community).

The build compiles the core library and the plugin against Java 21 so that they can be loaded into
the IDE's JetBrains Runtime.

## Editing assistance for `osm.json5`

The plugin provides IDE assistance for the OpenStreetMap interpretation configuration
(`terraminusminus/config/osm.json5`):

- **Autocompletion and documentation** from a bundled JSON Schema
  (`src/main/resources/schemas/osm.json`). The schema describes every mapper, match
  condition, dynamic value and draw function type, so key and value completion, hover
  documentation and schema-based validation work out of the box. The `osm.json5` format
  is JSON5, so comments and other lenient syntax are allowed.
- **A semantic inspection** (`Terra++ OSM configuration semantics`) for the parts of the
  format that JSON Schema cannot express: the format encodes typed unions and ordered
  lists as JSON objects whose keys repeat in document order, which is deliberately
  outside the JSON data model. The inspection reports unions with more than one or
  unknown members, empty lists, and binary `DValue` operators without exactly two
  operands, mirroring the errors Terra++ itself raises while parsing.
- **Duplicate-key suppression**: because repeated keys are the intended encoding in this
  format, IntelliJ's built-in `JsonDuplicatePropertyKeys` warning is suppressed inside
  `osm.json5` files.

The semantic rules live in the core library (`net.smyler.terrapreview.osmconfig.OsmConfigRules`)
so they are unit-tested independently of the IntelliJ platform. The format itself is
documented in `docs/osm-json5.md`.
