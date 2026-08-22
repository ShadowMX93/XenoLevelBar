package com.xenolevelbar;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class ConfigUpdateService {

    private final XenoLevelBarPlugin plugin;

    public ConfigUpdateService(XenoLevelBarPlugin plugin) {
        this.plugin = plugin;
    }

    public UpdateResult update() throws IOException, InvalidConfigurationException {
        plugin.saveDefaultConfig();

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration current = new YamlConfiguration();
        current.load(configFile);

        YamlConfiguration defaults = loadBundledDefaults();
        MergeResult merge = mergeMissingValues(current, defaults);
        Path backupFile = null;

        if (!merge.addedPaths().isEmpty()) {
            backupFile = configFile.toPath().resolveSibling("config.yml.bak");
            Files.copy(configFile.toPath(), backupFile, StandardCopyOption.REPLACE_EXISTING);
            current.save(configFile);
        }

        plugin.reloadConfig();
        return new UpdateResult(merge.addedPaths(), merge.conflicts(), backupFile);
    }

    private YamlConfiguration loadBundledDefaults() throws IOException, InvalidConfigurationException {
        try (InputStream input = plugin.getResource("config.yml")) {
            if (input == null) {
                throw new IOException("Bundled config.yml could not be found inside the plugin JAR.");
            }

            YamlConfiguration defaults = new YamlConfiguration();
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
            return defaults;
        }
    }

    static MergeResult mergeMissingValues(ConfigurationSection current, ConfigurationSection defaults) {
        List<String> addedPaths = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        mergeSection(current, defaults, "", addedPaths, conflicts);
        return new MergeResult(List.copyOf(addedPaths), List.copyOf(conflicts));
    }

    private static void mergeSection(
            ConfigurationSection current,
            ConfigurationSection defaults,
            String prefix,
            List<String> addedPaths,
            List<String> conflicts
    ) {
        for (String key : defaults.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object defaultValue = defaults.get(key);

            if (defaultValue instanceof ConfigurationSection defaultSection) {
                Object currentValue = current.get(key);
                ConfigurationSection currentSection;

                if (currentValue == null) {
                    currentSection = current.createSection(key);
                } else if (currentValue instanceof ConfigurationSection section) {
                    currentSection = section;
                } else {
                    conflicts.add(path);
                    continue;
                }

                mergeSection(currentSection, defaultSection, path, addedPaths, conflicts);
                continue;
            }

            if (current.get(key) == null) {
                current.set(key, defaultValue);
                addedPaths.add(path);
            }
        }
    }

    public record UpdateResult(List<String> addedPaths, List<String> conflicts, Path backupFile) {
        public boolean changed() {
            return !addedPaths.isEmpty();
        }
    }

    record MergeResult(List<String> addedPaths, List<String> conflicts) {
    }
}
