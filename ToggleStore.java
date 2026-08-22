package com.xenolevelbar;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ToggleStore {

    private final XenoLevelBarPlugin plugin;
    private final File file;
    private final Set<UUID> disabled = new HashSet<>();

    public ToggleStore(XenoLevelBarPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public boolean isDisabled(UUID uuid) {
        return disabled.contains(uuid);
    }

    public boolean toggle(UUID uuid) {
        boolean nowEnabled;
        if (disabled.remove(uuid)) {
            nowEnabled = true;
        } else {
            disabled.add(uuid);
            nowEnabled = false;
        }
        save();
        return nowEnabled;
    }

    private void load() {
        disabled.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("disabled")) {
            try {
                disabled.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid UUID in players.yml: " + raw);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> values = disabled.stream().map(UUID::toString).sorted().toList();
        yaml.set("disabled", values);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save players.yml: " + ex.getMessage());
        }
    }
}
