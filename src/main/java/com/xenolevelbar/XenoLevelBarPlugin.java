package com.xenolevelbar;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;
import java.util.Objects;

public final class XenoLevelBarPlugin extends JavaPlugin implements Listener {

    private ConfigUpdateService configUpdateService;
    private ToggleStore toggleStore;
    private XenoPlaceholderService placeholderService;
    private BossBarService bossBarService;
    private MessageService messageService;
    private UpdateService updateService;
    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        configUpdateService = new ConfigUpdateService(this);
        if (!applyConfigUpdates()) {
            getLogger().severe("Could not safely update config.yml. Disabling XenoLevelBar to protect the existing configuration.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

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
        updateService = new UpdateService(this);

        XenoLevelBarCommand command = new XenoLevelBarCommand(
                this,
                bossBarService,
                placeholderService,
                toggleStore,
                messageService,
                updateService
        );
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("xenolevelbar"), "xenolevelbar command missing from plugin.yml");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        Bukkit.getPluginManager().registerEvents(this, this);
        startUpdateTask();
        checkForUpdatesOnStartup();

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
        Player player = event.getPlayer();
        long delay = Math.max(0L, getConfig().getLong("join-delay-ticks", 10L));
        Bukkit.getScheduler().runTaskLater(this, () -> bossBarService.updatePlayer(player), delay);
        notifyAdminAboutCachedUpdate(player);
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

    public boolean reloadPlugin() {
        if (!applyConfigUpdates()) {
            return false;
        }
        messageService.reload();
        placeholderService.reload();
        bossBarService.reload();
        startUpdateTask();
        return true;
    }

    File pluginFile() {
        return getFile();
    }

    private boolean applyConfigUpdates() {
        try {
            ConfigUpdateService.UpdateResult result = configUpdateService.update();
            if (result.changed()) {
                getLogger().info(
                        "Updated config.yml with " + result.addedPaths().size() + " new option(s). Backup saved to "
                                + result.backupFile() + "."
                );
            }
            if (!result.conflicts().isEmpty()) {
                getLogger().warning(
                        "Could not auto-add config section(s) because existing values use those paths: "
                                + String.join(", ", result.conflicts())
                );
            }
            return true;
        } catch (Exception exception) {
            getLogger().severe("config.yml update failed: " + rootMessage(exception));
            return false;
        }
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

    private void checkForUpdatesOnStartup() {
        if (!getConfig().getBoolean("updater.enabled", true)
                || !getConfig().getBoolean("updater.check-on-startup", true)) {
            return;
        }

        updateService.checkAsync().whenComplete((check, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null) {
                getLogger().warning("Could not check GitHub for updates: " + rootMessage(error));
                return;
            }

            if (!check.updateAvailable()) {
                getLogger().info("Update check complete. XenoLevelBar " + check.currentVersion() + " is current.");
                return;
            }

            getLogger().info(
                    "XenoLevelBar " + check.latestVersion() + " is available (running " + check.currentVersion() + ")."
            );

            if (getConfig().getBoolean("updater.auto-download", false)) {
                stageStartupUpdate(check);
            }
        }));
    }

    private void stageStartupUpdate(UpdateService.UpdateCheck check) {
        updateService.downloadAsync(check).whenComplete((result, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null) {
                getLogger().warning("Could not download update: " + rootMessage(error));
                return;
            }
            getLogger().info(
                    "Downloaded XenoLevelBar " + result.update().latestVersion() + " to " + result.stagedFile()
                            + ". Restart the server to install it."
            );
        }));
    }

    private void notifyAdminAboutCachedUpdate(Player player) {
        if (!getConfig().getBoolean("updater.enabled", true)
                || !getConfig().getBoolean("updater.notify-admins", true)
                || !player.hasPermission(adminPermission())) {
            return;
        }

        updateService.cachedCheck()
                .filter(UpdateService.UpdateCheck::updateAvailable)
                .ifPresent(check -> messageService.send(
                        player,
                        "commands.update-available",
                        "&eUpdate available: &f%current% &7-> &a%latest%&7. Run &f/xlb update download&7 to stage it.",
                        Map.of(
                                "current", check.currentVersion(),
                                "latest", check.latestVersion(),
                                "release_url", check.releaseUrl()
                        )
                ));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
