# XenoLevelBar

A Paper plugin that displays **XenoLevels progression on a separate BossBar** while leaving Minecraft's vanilla XP bar and level untouched.

## Compatibility

XenoLevelBar is built against the **Paper 1.18 API** and emits **Java 17 bytecode** so the same plugin can be used across a broad range of Paper versions.

- Paper: **1.18.2+**
- Plugin bytecode: **Java 17**
- PlaceholderAPI: install a release compatible with your Minecraft/Paper version
- XenoLevels: install a release compatible with your Minecraft/Paper version

The server itself must still run the Java version required by that Paper release. For example, a modern Paper release may require a newer Java runtime even though XenoLevelBar itself is compiled for Java 17.

> XenoLevelBar can be compatible with Paper 1.18+, but XenoLevels and PlaceholderAPI must also support the specific server version you are running.

## Features

- Separate XenoLevels BossBar; vanilla XP is never changed
- Reads XenoLevels through official `%xlv_*%` PlaceholderAPI values
- Supports default or named XenoLevels systems
- Configurable BossBar title, color, style, flags and update timing
- MiniMessage formatting, including gradients, decorations, hover and click events
- Legacy `&`/`§` colors and `&#RRGGBB` hex colors (including mixed MiniMessage/legacy text)
- World whitelist/blacklist and gamemode visibility controls
- Fully separate `messages.yml` for commands, help, status output, states and hide reasons
- PlaceholderAPI placeholders inside player-facing messages and the BossBar title
- Per-player `/xlb toggle`, persisted in `players.yml`
- `/xlb status` explains why a HUD is hidden
- `/xlb reload` reloads both `config.yml` and `messages.yml`
- GitHub release updater with startup checks and manual download/staging commands
- Update downloads are validated as XenoLevelBar JARs before being staged for the next restart
- Safely handles temporary missing XenoLevels user data, including after `/xlv delete`

## Configuration files

- `config.yml` — behavior, updater settings, XenoLevels system, permissions, BossBar and visibility
- `messages.yml` — all player-facing command/help/status/updater text
- `players.yml` — generated automatically; stores players who toggled the HUD off

## Build

### Gradle (recommended)

Linux / macOS:

```bash
chmod +x gradlew
./gradlew clean test build
```

Windows:

```bat
gradlew.bat clean test build
```

Gradle output:

```text
build/libs/XenoLevelBar-1.1.2.jar
```

The included Gradle bootstrap scripts download the pinned Gradle distribution automatically on first use. Build with **JDK 17 or newer**; the Java compiler is forced to output Java 17-compatible bytecode.

### Maven

```bash
mvn clean package
```

Maven output:

```text
target/XenoLevelBar-1.1.2.jar
```

See `BUILDING.md` for Linux and Windows details.

## Install / upgrade

1. Put the JAR in `plugins/`.
2. Make sure PlaceholderAPI and XenoLevels versions compatible with your server are installed.
3. Fully restart Paper.
4. Edit `plugins/XenoLevelBar/config.yml` and `messages.yml` as desired.
5. Run `/xlb status`.

Existing `players.yml`, `config.yml`, and `messages.yml` can be kept when upgrading.

### Built-in updater

The updater checks the latest GitHub Release asynchronously and never blocks the main server thread.

Default behavior:

- `updater.enabled: true`
- `updater.check-on-startup: true`
- `updater.auto-download: false`
- `updater.notify-admins: true`

Use `/xlb update check` to check GitHub manually. Use `/xlb update download` to download and validate the latest release JAR and stage it in Bukkit/Paper's configured update folder. A full server restart is required to install the staged update.

Set `updater.auto-download: true` if you want new releases automatically downloaded after the startup check. Installation still waits for a normal restart; the plugin does not hot-swap its live JAR.

## Commands

- `/xlb toggle` — hide/show your XenoLevels BossBar
- `/xlb status` — diagnostics, current values, and HUD visibility reason
- `/xlb reload` — reload `config.yml` and `messages.yml` (admin)
- `/xlb update check` — check the latest GitHub Release (admin)
- `/xlb update download` — download and stage the latest release for restart (admin)
- `/xlb help` — command help

Aliases: `/xlbar`, `/xenolevelbar`

## Permissions

- `xenolevelbar.use` — default: everyone
- `xenolevelbar.admin` — default: operators; also controls updater commands

The nodes used by XenoLevelBar can be changed under `permissions:` in `config.yml`.

## BossBar tokens

`%level%`, `%exp%`, `%required%`, `%remaining%`, `%percent%`, `%max_level%`, `%system%`

Normal PlaceholderAPI placeholders may also be used in the BossBar title.

Both the BossBar title and every entry in `messages.yml` support MiniMessage,
legacy `&`/`§` codes and `&#RRGGBB` hex colors. Examples:

```yaml
title: "<gradient:#D14CFF:#55FFFF><bold>Level %level%</bold></gradient> &8• &f%percent%%"
prefix: "<dark_gray>[<gradient:#D14CFF:#55FFFF>XenoLevelBar</gradient>]</dark_gray> "
```

MiniMessage click and hover events are preserved in chat messages. BossBar titles
only display visual formatting because Minecraft BossBars do not support events.

## Important

A server-only Paper plugin cannot create a literal second native vanilla XP bar at the bottom of an unmodified Minecraft client. XenoLevelBar therefore uses a BossBar while keeping vanilla XP completely independent.

## v1.1.2

- Added MiniMessage formatting to BossBar titles and all player-facing messages.
- Preserved legacy `&`, `§` and `&#RRGGBB` color support, including mixed formatting.
- Added MiniMessage click and hover events to chat messages.
- Added safe fallback behavior for malformed MiniMessage input.
- Updated the minimum supported Paper version to 1.18.2.

## v1.1.0

- Added asynchronous GitHub Release update checks.
- Added `/xlb update check` and `/xlb update download` admin commands.
- Added optional startup auto-download and admin update notifications.
- Downloads are validated against `plugin.yml` before being staged in the Bukkit/Paper update folder.
- Added JUnit coverage for updater version comparison and branch CI testing.

## v1.0.0

- First public release of XenoLevelBar.
- Displays XenoLevels progression on a separate, configurable BossBar.
- Keeps Minecraft's vanilla XP bar and level untouched.
- Supports Paper 1.18+, Java 17 bytecode, PlaceholderAPI, and named XenoLevels systems.
- Includes configurable visibility, messages, permissions, commands, and persistent per-player toggles.
