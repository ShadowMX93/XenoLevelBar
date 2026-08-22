package com.xenolevelbar;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateService {

    private static final URI LATEST_RELEASE_URI = URI.create(
            "https://api.github.com/repos/ShadowMX93/XenoLevelBar/releases/latest"
    );
    private static final Pattern ASSET_PATTERN = Pattern.compile(
            "\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+\\.jar)\\\".*?" +
                    "\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.DOTALL
    );

    private final XenoLevelBarPlugin plugin;
    private final HttpClient httpClient;
    private volatile UpdateCheck cachedCheck;

    public UpdateService(XenoLevelBarPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CompletableFuture<UpdateCheck> checkAsync() {
        int timeoutSeconds = timeoutSeconds();
        return async(() -> check(timeoutSeconds));
    }

    public CompletableFuture<DownloadResult> downloadLatestAsync() {
        return checkAsync().thenCompose(check -> {
            if (!check.updateAvailable()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("XenoLevelBar is already up to date.")
                );
            }
            return downloadAsync(check);
        });
    }

    public CompletableFuture<DownloadResult> downloadAsync(UpdateCheck check) {
        if (check == null || !check.updateAvailable()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No newer release is available to download.")
            );
        }

        int timeoutSeconds = timeoutSeconds();
        File updateFolder = Bukkit.getUpdateFolderFile();
        String installedJarName = plugin.pluginFile().getName();
        return async(() -> download(check, updateFolder.toPath(), installedJarName, timeoutSeconds));
    }

    public Optional<UpdateCheck> cachedCheck() {
        return Optional.ofNullable(cachedCheck);
    }

    private UpdateCheck check(int timeoutSeconds) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_URI)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", userAgent())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() == 404) {
            throw new IOException("No GitHub release has been published yet.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned HTTP " + response.statusCode() + ".");
        }

        String body = response.body();
        String tag = jsonString(body, "tag_name")
                .orElseThrow(() -> new IOException("GitHub release did not contain a tag name."));
        String latestVersion = normalizeVersion(tag);
        String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
        String releaseUrl = jsonString(body, "html_url").orElse(LATEST_RELEASE_URI.toString());

        ReleaseAsset asset = findJarAsset(body, latestVersion)
                .orElseThrow(() -> new IOException("Latest release does not contain an XenoLevelBar JAR asset."));

        UpdateCheck result = new UpdateCheck(
                currentVersion,
                latestVersion,
                tag,
                releaseUrl,
                asset.name(),
                asset.uri(),
                compareVersions(latestVersion, currentVersion) > 0
        );
        cachedCheck = result;
        return result;
    }

    private DownloadResult download(
            UpdateCheck check,
            Path updateFolder,
            String installedJarName,
            int timeoutSeconds
    ) throws IOException, InterruptedException {
        Files.createDirectories(updateFolder);
        Path temporaryFile = Files.createTempFile(updateFolder, installedJarName + ".", ".tmp");

        try {
            HttpRequest request = HttpRequest.newBuilder(check.assetUrl())
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", userAgent())
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream ignored = response.body()) {
                    // Close the response body before failing.
                }
                throw new IOException("GitHub download returned HTTP " + response.statusCode() + ".");
            }

            try (InputStream input = response.body()) {
                Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }

            if (Files.size(temporaryFile) == 0L) {
                throw new IOException("Downloaded update is empty.");
            }

            validateJar(temporaryFile, check.latestVersion());

            Path target = updateFolder.resolve(installedJarName);
            try {
                Files.move(
                        temporaryFile,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new DownloadResult(check, target);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            Files.deleteIfExists(temporaryFile);
            throw exception;
        }
    }

    private void validateJar(Path jarPath, String expectedVersion) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml == null) {
                throw new IOException("Downloaded JAR does not contain plugin.yml.");
            }

            YamlConfiguration description;
            try (InputStream input = jar.getInputStream(pluginYml);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                description = YamlConfiguration.loadConfiguration(reader);
            }

            String name = description.getString("name", "");
            if (!plugin.getName().equals(name)) {
                throw new IOException("Downloaded JAR is for plugin '" + name + "', not " + plugin.getName() + ".");
            }

            String packagedVersion = normalizeVersion(description.getString("version", ""));
            if (!expectedVersion.equals(packagedVersion)) {
                throw new IOException(
                        "Downloaded JAR version " + packagedVersion + " does not match release " + expectedVersion + "."
                );
            }
        }
    }

    private Optional<ReleaseAsset> findJarAsset(String json, String latestVersion) {
        Matcher matcher = ASSET_PATTERN.matcher(json);
        ReleaseAsset fallback = null;
        String expectedName = "XenoLevelBar-" + latestVersion + ".jar";

        while (matcher.find()) {
            String name = unescapeJson(matcher.group(1));
            if (!name.toLowerCase().endsWith(".jar")) {
                continue;
            }
            if (!name.startsWith("XenoLevelBar")) {
                continue;
            }

            URI uri;
            try {
                uri = URI.create(unescapeJson(matcher.group(2)));
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ReleaseAsset asset = new ReleaseAsset(name, uri);
            if (expectedName.equals(name)) {
                return Optional.of(asset);
            }
            if (fallback == null) {
                fallback = asset;
            }
        }
        return Optional.ofNullable(fallback);
    }

    private Optional<String> jsonString(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""
        );
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(unescapeJson(matcher.group(1)));
    }

    private <T> CompletableFuture<T> async(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(task.call());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private int timeoutSeconds() {
        return Math.max(3, plugin.getConfig().getInt("updater.timeout-seconds", 10));
    }

    private String userAgent() {
        return "XenoLevelBar/" + plugin.getDescription().getVersion();
    }

    static int compareVersions(String left, String right) {
        Version a = Version.parse(left);
        Version b = Version.parse(right);

        int length = Math.max(a.core().length, b.core().length);
        for (int i = 0; i < length; i++) {
            long av = i < a.core().length ? a.core()[i] : 0L;
            long bv = i < b.core().length ? b.core()[i] : 0L;
            int comparison = Long.compare(av, bv);
            if (comparison != 0) {
                return comparison;
            }
        }

        if (a.prerelease().isEmpty() && b.prerelease().isEmpty()) {
            return 0;
        }
        if (a.prerelease().isEmpty()) {
            return 1;
        }
        if (b.prerelease().isEmpty()) {
            return -1;
        }
        return a.prerelease().compareToIgnoreCase(b.prerelease());
    }

    private static String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int buildMetadata = normalized.indexOf('+');
        if (buildMetadata >= 0) {
            normalized = normalized.substring(0, buildMetadata);
        }
        return normalized;
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public record UpdateCheck(
            String currentVersion,
            String latestVersion,
            String tag,
            String releaseUrl,
            String assetName,
            URI assetUrl,
            boolean updateAvailable
    ) {
    }

    public record DownloadResult(UpdateCheck update, Path stagedFile) {
    }

    private record ReleaseAsset(String name, URI uri) {
    }

    private record Version(long[] core, String prerelease) {
        private static Version parse(String raw) {
            String normalized = normalizeVersion(raw);
            String[] split = normalized.split("-", 2);
            String[] coreParts = split[0].split("\\.");
            long[] core = Arrays.stream(coreParts)
                    .mapToLong(Version::numericPart)
                    .toArray();
            String prerelease = split.length > 1 ? split[1] : "";
            return new Version(core, prerelease);
        }

        private static long numericPart(String part) {
            try {
                return Long.parseLong(part.replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
    }
}
