package com.xenolevelbar;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class XenoLevelBarPlugin extends JavaPlugin implements Listener {

    private ToggleStore toggleStore;
    private XenoPlaceholderService placeholderService;
    private BossBarService bossBarService;
    private MessageService messageService;
    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messageService = new MessageService(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("PlaceholderAPI is required. Disabling XenoLevelBar.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("XenoLevels") == null) {
            getLogger().warning("XenoLevels was not detected. The plugin will stay loaded, but %xlv_*% placeholders will not resolve until XenoLevels is installed/enabled.");
        }

        toggleStore = new ToggleStore(this);
        placeholderService = new XenoPlaceholderService(this);
        bossBarService = new BossBarService(this, placeholderService, toggleStore);

        XenoLevelBarCommand command = new XenoLevelBarCommand(this, bossBarService, placeholderService, toggleStore, messageService);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("xenolevelbar"), "xenolevelbar command missing from plugin.yml");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        Bukkit.getPluginManager().registerEvents(this, this);
        startUpdateTask();

        Bukkit.getOnlinePlayers().forEach(bossBarService::updatePlayer);
        getLogger().info("XenoLevelBar enabled. Vanilla Minecraft XP is not modified.");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (bossBarService != null) {
            bossBarService.removeAll();
        }
        if (toggleStore != null) {
            toggleStore.save();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        long delay = Math.max(0L, getConfig().getLong("join-delay-ticks", 10L));
        Bukkit.getScheduler().runTaskLater(this, () -> bossBarService.updatePlayer(event.getPlayer()), delay);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bossBarService.removePlayer(event.getPlayer());
        placeholderService.forget(event.getPlayer().getUniqueId());
    }

    public String viewPermission() {
        return getConfig().getString("permissions.view", getConfig().getString("permission", "xenolevelbar.use"));
    }

    public String adminPermission() {
        return getConfig().getString("permissions.admin", "xenolevelbar.admin");
    }

    public void reloadPlugin() {
        reloadConfig();
        messageService.reload();
        placeholderService.reload();
        bossBarService.reload();
        startUpdateTask();
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        long ticks = Math.max(1L, getConfig().getLong("update-ticks", 10L));
        updateTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("enabled", true)) {
                bossBarService.removeAll();
                return;
            }
            Bukkit.getOnlinePlayers().forEach(bossBarService::updatePlayer);
        }, 1L, ticks);
    }
}
