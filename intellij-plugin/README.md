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
