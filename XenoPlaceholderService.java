package com.xenolevelbar;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class XenoPlaceholderService {

    private static final String SEPARATOR = "\u001F";

    private final XenoLevelBarPlugin plugin;
    private final Map<UUID, Long> retryAfterMillis = new HashMap<>();
    private String system;
    private long failureRetryMillis;

    public XenoPlaceholderService(XenoLevelBarPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        system = plugin.getConfig().getString("system", "default").trim().toLowerCase(Locale.ROOT);
        if (system.isBlank()) {
            system = "default";
        }

        long retryTicks = Math.max(1L, plugin.getConfig().getLong("placeholder-failure-retry-ticks", 40L));
        failureRetryMillis = retryTicks * 50L;
        retryAfterMillis.clear();
    }

    public String system() {
        return system;
    }

    public LevelSnapshot snapshot(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long retryAt = retryAfterMillis.get(uuid);
        if (retryAt != null && now < retryAt) {
            return unavailable("xenolevels-user-unavailable");
        }

        String combined = String.join(SEPARATOR,
                key("level"),
                key("exp"),
                key("exp_required"),
                key("exp_remaining"),
                key("exp_percent"),
                key("level_maximum")
        );

        final String resolved;
        try {
            // Resolve all XenoLevels values in a single PAPI call. If XenoLevels has
            // temporarily deleted/unloaded its XLVUser, its expansion may throw.
            // We deliberately contain that exception here so it never escapes into
            // Paper's repeating scheduler task.
            resolved = PlaceholderAPI.setPlaceholders(player, combined);
        } catch (RuntimeException ex) {
            retryAfterMillis.put(uuid, now + failureRetryMillis);
            return unavailable("xenolevels-user-unavailable");
        }

        if (resolved == null) {
            return unavailable("invalid-data");
        }

        String[] parts = resolved.split(SEPARATOR, -1);
        if (parts.length != 6) {
            return unavailable("invalid-data");
        }

        String level = parts[0];
        String exp = parts[1];
        String required = parts[2];
        String remaining = parts[3];
        String percent = parts[4];
        String maxLevel = parts[5];

        boolean valid = !isUnresolved(level)
                && !isUnresolved(exp)
                && !isUnresolved(required)
                && !isUnresolved(percent);

        if (!valid) {
            return new LevelSnapshot(
                    level,
                    exp,
                    required,
                    remaining,
                    percent,
                    maxLevel,
                    0.0,
                    false,
                    false,
                    "invalid-data"
            );
        }

        retryAfterMillis.remove(uuid);

        Double expNumber = parseNumber(exp);
        Double requiredNumber = parseNumber(required);
        Double percentNumber = parseNumber(percent);
        Double levelNumber = parseNumber(level);
        Double maxLevelNumber = parseNumber(maxLevel);

        double progress = 0.0;
        if (expNumber != null && requiredNumber != null && requiredNumber > 0.0) {
            progress = expNumber / requiredNumber;
        } else if (percentNumber != null) {
            progress = percentNumber > 1.0 ? percentNumber / 100.0 : percentNumber;
        }
        progress = clamp(progress);

        boolean max = levelNumber != null
                && maxLevelNumber != null
                && maxLevelNumber > 0
                && levelNumber >= maxLevelNumber;

        return new LevelSnapshot(
                level,
                exp,
                required,
                remaining,
                normalizePercent(percent, progress),
                maxLevel,
                progress,
                true,
                max,
                "shown"
        );
    }

    public String parseOtherPlaceholders(Player player, String text) {
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (RuntimeException ex) {
            retryAfterMillis.put(player.getUniqueId(), System.currentTimeMillis() + failureRetryMillis);
            return text;
        }
    }

    public void forget(UUID uuid) {
        retryAfterMillis.remove(uuid);
    }

    private LevelSnapshot unavailable(String reasonKey) {
        return new LevelSnapshot(
                null,
                null,
                null,
                null,
                null,
                null,
                0.0,
                false,
                false,
                reasonKey
        );
    }

    private String key(String suffix) {
        if (system.equals("default")) {
            return "%xlv_" + suffix + "%";
        }
        return "%xlv_" + system + "_" + suffix + "%";
    }

    private static boolean isUnresolved(String value) {
        return value == null || value.isBlank() || value.contains("%xlv_");
    }

    private static Double parseNumber(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replace(",", "")
                .replace("%", "")
                .replaceAll("§[0-9A-FK-ORa-fk-or]", "")
                .replaceAll("[^0-9+\\-.]", "")
                .trim();
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizePercent(String raw, double progress) {
        Double parsed = parseNumber(raw);
        if (parsed == null) {
            return String.format(Locale.US, "%.1f", progress * 100.0);
        }
        double value = parsed <= 1.0 && raw != null && !raw.contains("%") ? parsed * 100.0 : parsed;
        if (Math.rint(value) == value) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record LevelSnapshot(
            String level,
            String exp,
            String required,
            String remaining,
            String percent,
            String maxLevel,
            double progress,
            boolean valid,
            boolean maxLevelReached,
            String reasonKey
    ) {}
}
