# Terrapreview IntelliJ Plugin

A proof-of-concept [IntelliJ Platform](https://plugins.jetbrains.com/docs/intellij/) plugin that embeds
the Terrapreview live world preview in the IDE, as a tool window, for use while editing
[Terra--](https://github.com/SmylerMC/terraminusminus) / Terra++ configuration files.

## What it does

When the **Terrapreview** tool window is opened, the plugin:

1. Starts the Terrapreview web server (the same one the CLI runs) on an ephemeral loopback port,
   watching `<project>/terraminusminus/config` for configuration changes, and
2. Shows the preview page in an embedded Chromium (JCEF) pane, which live-reloads through
   the same server-sent events the browser UI uses.

If JCEF is not available, it falls back to a panel with a button opening the preview in the
system browser.

## Build

The plugin is a subproject of the root build:

```sh
./gradlew :intellij-plugin:build
```

The distributable plugin is `ide-plugin/build/libs/*.jar` (or use `./gradlew :intellij-plugin:runIde`
to try it in a sandboxed IDEA Community).

The build compiles the core library and the plugin against Java 21 so that they can be loaded into
the IDE's JetBrains Runtime.
