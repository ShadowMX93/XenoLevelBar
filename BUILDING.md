# Building XenoLevelBar

XenoLevelBar can be built with either Gradle or Maven. Gradle is the recommended path and the repository includes Linux/macOS and Windows bootstrap scripts.

## Requirements

- JDK **17 or newer** installed
- Internet access on the first build so Gradle/Paper API/PlaceholderAPI dependencies can be downloaded

The project compiles with `--release 17`, so using a newer JDK still produces Java 17-compatible plugin bytecode.

Check Java:

```text
java -version
javac -version
```

## Linux / macOS

From the repository root:

```bash
chmod +x gradlew
./gradlew clean test build
```

Output:

```text
build/libs/XenoLevelBar-1.1.0.jar
```

## Windows

Open Command Prompt or PowerShell in the repository root:

```bat
gradlew.bat clean test build
```

Output:

```text
build\libs\XenoLevelBar-1.1.0.jar
```

## Installed Gradle

If you already have a compatible Gradle installation:

```text
gradle clean test build
```

The included bootstrap scripts pin Gradle 9.7.1 so contributors on Linux and Windows use the same Gradle release.

## Maven

Maven remains supported:

```text
mvn clean package
```

Output:

```text
target/XenoLevelBar-1.1.0.jar
```

## Compatibility target

Both build systems compile against:

```text
Paper API:       1.18-R0.1-SNAPSHOT
PlaceholderAPI:  2.11.6 (compile-only API)
Java release:    17
plugin api-version: 1.18
```

Because the dependencies are compile-only, they are not bundled into XenoLevelBar. At runtime you should install PlaceholderAPI and XenoLevels versions appropriate for the Paper/Minecraft version of the server.
