package com.xenolevelbar;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdateServiceTest {

    @Test
    void addsMissingDefaultsWithoutOverwritingExistingValues() {
        YamlConfiguration current = new YamlConfiguration();
        current.set("enabled", false);
        current.set("bossbar.color", "BLUE");

        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("enabled", true);
        defaults.set("bossbar.color", "PURPLE");
        defaults.set("bossbar.hide-at-max-level", false);
        defaults.set("updater.enabled", true);

        ConfigUpdateService.MergeResult result = ConfigUpdateService.mergeMissingValues(current, defaults);

        assertFalse(current.getBoolean("enabled"));
        assertEquals("BLUE", current.getString("bossbar.color"));
        assertFalse(current.getBoolean("bossbar.hide-at-max-level"));
        assertTrue(current.getBoolean("updater.enabled"));
        assertEquals(List.of("bossbar.hide-at-max-level", "updater.enabled"), result.addedPaths());
        assertTrue(result.conflicts().isEmpty());
    }

    @Test
    void copiesMissingListsAsCompleteDefaultValues() {
        YamlConfiguration current = new YamlConfiguration();
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("visibility.gamemodes", List.of("SURVIVAL", "ADVENTURE"));

        ConfigUpdateService.MergeResult result = ConfigUpdateService.mergeMissingValues(current, defaults);

        assertEquals(List.of("SURVIVAL", "ADVENTURE"), current.getStringList("visibility.gamemodes"));
        assertEquals(List.of("visibility.gamemodes"), result.addedPaths());
    }

    @Test
    void preservesConflictingUserValueInsteadOfReplacingItWithASection() {
        YamlConfiguration current = new YamlConfiguration();
        current.set("updater", false);

        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("updater.enabled", true);
        defaults.set("updater.auto-download", false);

        ConfigUpdateService.MergeResult result = ConfigUpdateService.mergeMissingValues(current, defaults);

        assertFalse(current.getBoolean("updater"));
        assertEquals(List.of("updater"), result.conflicts());
        assertTrue(result.addedPaths().isEmpty());
    }
}
