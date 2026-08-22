package com.xenolevelbar;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BossBarService {

    private final XenoLevelBarPlugin plugin;
    private final XenoPlaceholderService placeholders;
    private final ToggleStore toggleStore;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    private String permission;
    private String titleTemplate;
    private BarColor barColor;
    private BarStyle barStyle;
    private Set<BarFlag> barFlags;
    private boolean hideAtMax;
    private Set<String> enabledWorlds;
    private Set<String> disabledWorlds;
    private Set<GameMode> allowedGameModes;

    public BossBarService(XenoLevelBarPlugin plugin, XenoPlaceholderService placeholders, ToggleStore toggleStore) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.toggleStore = toggleStore;
        reloadSettings();
    }

    public void reload() {
        removeAll();
        reloadSettings();
        Bukkit.getOnlinePlayers().forEach(this::updatePlayer);
    }

    public void updatePlayer(Player player) {
        DisplayState base = baseDisplayState(player);
        if (!base.shouldDisplay()) {
            removePlayer(player);
            return;
        }

        XenoPlaceholderService.LevelSnapshot snapshot = placeholders.snapshot(player);
        if (!snapshot.valid()) {
            removePlayer(player);
            return;
        }
        if (hideAtMax && snapshot.maxLevelReached()) {
            removePlayer(player);
            return;
        }

        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> createBar(player));

        String title = placeholders.parseOtherPlaceholders(player, titleTemplate)
                .replace("%level%", safe(snapshot.level()))
                .replace("%exp%", safe(snapshot.exp()))
                .replace("%required%", safe(snapshot.required()))
                .replace("%remaining%", safe(snapshot.remaining()))
                .replace("%percent%", safe(snapshot.percent()))
                .replace("%max_level%", safe(snapshot.maxLevel()))
                .replace("%system%", placeholders.system());

        bar.setTitle(MessageService.color(title));
        bar.setProgress(snapshot.progress());
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        bar.setVisible(true);
    }

    public void removePlayer(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    public void removeAll() {
        bars.values().forEach(bar -> {
            bar.removeAll();
            bar.setVisible(false);
        });
        bars.clear();
    }

    public boolean isVisible(Player player) {
        return bars.containsKey(player.getUniqueId());
    }

    public DisplayState displayState(Player player) {
        DisplayState base = baseDisplayState(player);
        if (!base.shouldDisplay()) {
            return base;
        }

        XenoPlaceholderService.LevelSnapshot snapshot = placeholders.snapshot(player);
        if (!snapshot.valid()) {
            return new DisplayState(false, snapshot.reasonKey());
        }
        if (hideAtMax && snapshot.maxLevelReached()) {
            return new DisplayState(false, "max-level");
        }
        return new DisplayState(true, isVisible(player) ? "shown" : "waiting-update");
    }

    private DisplayState baseDisplayState(Player player) {
        if (!plugin.getConfig().getBoolean("enabled", true)) {
            return new DisplayState(false, "plugin-disabled");
        }
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            return new DisplayState(false, "missing-permission");
        }
        if (toggleStore.isDisabled(player.getUniqueId())) {
            return new DisplayState(false, "player-toggle");
        }

        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(world)) {
            return new DisplayState(false, "world-not-enabled");
        }
        if (disabledWorlds.contains(world)) {
            return new DisplayState(false, "disabled-world");
        }
        if (!allowedGameModes.isEmpty() && !allowedGameModes.contains(player.getGameMode())) {
            return new DisplayState(false, "gamemode");
        }
        return new DisplayState(true, "shown");
    }

    private BossBar createBar(Player player) {
        BossBar bar = Bukkit.createBossBar("", barColor, barStyle, barFlags.toArray(BarFlag[]::new));
        bar.addPlayer(player);
        bar.setVisible(true);
        return bar;
    }

    private void reloadSettings() {
        permission = plugin.getConfig().getString(
                "permissions.view",
                plugin.getConfig().getString("permission", "xenolevelbar.use")
        );
        titleTemplate = plugin.getConfig().getString(
                "bossbar.title",
                "&dLevel &f%level% &8• &b%exp%&7/&b%required% EXP &8(&f%percent%%&8)"
        );
        barColor = enumValue(BarColor.class, plugin.getConfig().getString("bossbar.color", "PURPLE"), BarColor.PURPLE);
        barStyle = enumValue(BarStyle.class, plugin.getConfig().getString("bossbar.style", "SOLID"), BarStyle.SOLID);
        hideAtMax = plugin.getConfig().getBoolean("bossbar.hide-at-max-level", false);

        barFlags = EnumSet.noneOf(BarFlag.class);
        for (String raw : plugin.getConfig().getStringList("bossbar.flags")) {
            try {
                barFlags.add(BarFlag.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown bossbar flag in config.yml: " + raw);
            }
        }

        enabledWorlds = lowerCaseSet(plugin.getConfig().getStringList("visibility.enabled-worlds"));
        disabledWorlds = lowerCaseSet(plugin.getConfig().getStringList("visibility.disabled-worlds"));

        allowedGameModes = EnumSet.noneOf(GameMode.class);
        for (String raw : plugin.getConfig().getStringList("visibility.gamemodes")) {
            try {
                allowedGameModes.add(GameMode.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown gamemode in config.yml: " + raw);
            }
        }
    }

    private static Set<String> lowerCaseSet(Iterable<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            result.add(value.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value == null ? "?" : value;
    }

    public record DisplayState(boolean shouldDisplay, String reasonKey) {}
}
