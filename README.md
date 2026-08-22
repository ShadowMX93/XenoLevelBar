# XenoLevelBar

A Paper plugin that displays **XenoLevels progression on a separate BossBar** while leaving Minecraft's vanilla XP bar and level untouched.

## Compatibility

XenoLevelBar is built against the **Paper 1.18 API** and emits **Java 17 bytecode** so the same plugin can be used across a broad range of Paper versions.

- Paper: **1.18+**
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
- Legacy `&` colors and `&#RRGGBB` hex colors
- World whitelist/blacklist and gamemode visibility controls
- Fully separate `messages.yml` for commands, help, status output, states and hide reasons
- PlaceholderAPI placeholders inside player-facing messages and the BossBar title
- Per-player `/xlb toggle`, persisted in `players.yml`
- `/xlb status` explains why a HUD is hidden
- `/xlb reload` reloads both `config.yml` and `messages.yml`
- Safely handles temporary missing XenoLevels user data, including after `/xlv delete`

## Configuration files

- `config.yml` — behavior, XenoLevels system, permissions, BossBar and visibility
- `messages.yml` — all player-facing command/help/status text
- `players.yml` — generated automatically; stores players who toggled the HUD off

## Build

### Gradle (recommended)

Linux / macOS:

```bash
chmod +x gradlew
./gradlew clean build
```

Windows:

```bat
gradlew.bat clean build
```

Gradle output:

```text
build/libs/XenoLevelBar-1.0.0.jar
```

The included Gradle bootstrap scripts download the pinned Gradle distribution automatically on first use. Build with **JDK 17 or newer**; the Java compiler is forced to output Java 17-compatible bytecode.

### Maven

```bash
mvn clean package
```

Maven output:

```text
target/XenoLevelBar-1.0.0.jar
```

See `BUILDING.md` for Linux and Windows details.

## Install / upgrade

1. Put the JAR in `plugins/`.
2. Make sure PlaceholderAPI and XenoLevels versions compatible with your server are installed.
3. Fully restart Paper.
4. Edit `plugins/XenoLevelBar/config.yml` and `messages.yml` as desired.
5. Run `/xlb status`.

Existing `players.yml`, `config.yml`, and `messages.yml` can be kept when upgrading.

## Commands

- `/xlb toggle` — hide/show your XenoLevels BossBar
- `/xlb status` — diagnostics, current values, and HUD visibility reason
- `/xlb reload` — reload `config.yml` and `messages.yml` (admin)
- `/xlb help` — command help

Aliases: `/xlbar`, `/xenolevelbar`

## Permissions

- `xenolevelbar.use` — default: everyone
- `xenolevelbar.admin` — default: operators

The nodes used by XenoLevelBar can be changed under `permissions:` in `config.yml`.

## BossBar tokens

`%level%`, `%exp%`, `%required%`, `%remaining%`, `%percent%`, `%max_level%`, `%system%`

Normal PlaceholderAPI placeholders may also be used in the BossBar title.

## Important

A server-only Paper plugin cannot create a literal second native vanilla XP bar at the bottom of an unmodified Minecraft client. XenoLevelBar therefore uses a BossBar while keeping vanilla XP completely independent.

## v1.0.0

- First public release of XenoLevelBar.
- Displays XenoLevels progression on a separate, configurable BossBar.
- Keeps Minecraft's vanilla XP bar and level untouched.
- Supports Paper 1.18+, Java 17 bytecode, PlaceholderAPI, and named XenoLevels systems.
- Includes configurable visibility, messages, permissions, commands, and persistent per-player toggles.
