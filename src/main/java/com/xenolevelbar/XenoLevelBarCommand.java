package com.xenolevelbar;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class XenoLevelBarCommand implements CommandExecutor, TabCompleter {

    private final XenoLevelBarPlugin plugin;
    private final BossBarService bossBars;
    private final XenoPlaceholderService placeholders;
    private final ToggleStore toggles;
    private final MessageService messages;
    private final UpdateService updates;

    public XenoLevelBarCommand(
            XenoLevelBarPlugin plugin,
            BossBarService bossBars,
            XenoPlaceholderService placeholders,
            ToggleStore toggles,
            MessageService messages,
            UpdateService updates
    ) {
        this.plugin = plugin;
        this.bossBars = bossBars;
        this.placeholders = placeholders;
        this.toggles = toggles;
        this.messages = messages;
        this.updates = updates;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "toggle" -> toggle(sender);
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            case "update" -> update(sender, args);
            case "help" -> help(sender, label);
            default -> help(sender, label);
        }
        return true;
    }

    private void toggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.player-only", "&cOnly players can use this command.");
            return;
        }
        if (!player.hasPermission(plugin.viewPermission())) {
            messages.send(player, "commands.no-permission", "&cYou do not have permission to do that.");
            return;
        }

        boolean enabled = toggles.toggle(player.getUniqueId());
        if (enabled) {
            bossBars.updatePlayer(player);
            messages.send(player, "commands.toggle-enabled", "Your XenoLevels bar is now &aenabled&7.");
        } else {
            bossBars.removePlayer(player);
            messages.send(player, "commands.toggle-disabled", "Your XenoLevels bar is now &cdisabled&7.");
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            messages.send(sender, "commands.no-permission", "&cYou do not have permission to do that.");
            return;
        }
        plugin.reloadPlugin();
        messages.send(sender, "commands.reloaded", "Configuration and messages reloaded.");
    }

    private void update(CommandSender sender, String[] args) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            messages.send(sender, "commands.no-permission", "&cYou do not have permission to do that.");
            return;
        }
        if (!plugin.getConfig().getBoolean("updater.enabled", true)) {
            messages.send(sender, "commands.update-disabled", "&eThe updater is disabled in config.yml.");
            return;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "check";
        switch (action) {
            case "check" -> checkUpdate(sender);
            case "download" -> downloadUpdate(sender);
            default -> messages.send(
                    sender,
                    "commands.update-usage",
                    "&eUsage: /xlb update <check|download>"
            );
        }
    }

    private void checkUpdate(CommandSender sender) {
        messages.send(sender, "commands.update-checking", "&eChecking GitHub for updates...");
        updates.checkAsync().whenComplete((check, error) -> runSync(() -> {
            if (error != null) {
                sendUpdateError(sender, error);
                return;
            }

            Map<String, String> tokens = updateTokens(check);
            if (check.updateAvailable()) {
                messages.send(
                        sender,
                        "commands.update-available",
                        "&eUpdate available: &f%current% &7-> &a%latest%&7. Run &f/xlb update download&7 to stage it.",
                        tokens
                );
            } else {
                messages.send(
                        sender,
                        "commands.update-current",
                        "&aYou are running the latest release (&f%current%&a).",
                        tokens
                );
            }
        }));
    }

    private void downloadUpdate(CommandSender sender) {
        messages.send(sender, "commands.update-checking", "&eChecking GitHub for updates...");
        updates.checkAsync().whenComplete((check, checkError) -> runSync(() -> {
            if (checkError != null) {
                sendUpdateError(sender, checkError);
                return;
            }
            if (!check.updateAvailable()) {
                messages.send(
                        sender,
                        "commands.update-current",
                        "&aYou are running the latest release (&f%current%&a).",
                        updateTokens(check)
                );
                return;
            }

            messages.send(
                    sender,
                    "commands.update-download-start",
                    "&eDownloading XenoLevelBar %latest%...",
                    updateTokens(check)
            );

            updates.downloadAsync(check).whenComplete((result, downloadError) -> runSync(() -> {
                if (downloadError != null) {
                    sendUpdateError(sender, downloadError);
                    return;
                }

                Map<String, String> tokens = new LinkedHashMap<>(updateTokens(result.update()));
                tokens.put("path", result.stagedFile().toString());
                messages.send(
                        sender,
                        "commands.update-downloaded",
                        "&aDownloaded %latest% to &f%path%&a. Restart the server to install it.",
                        tokens
                );
            }));
        }));
    }

    private void help(CommandSender sender, String label) {
        Map<String, String> tokens = Map.of("label", label);
        for (String line : messages.getList("help", List.of(
                "&8&m--------------------------------",
                "&dXenoLevelBar &7commands",
                "&f/%label% toggle &8- &7Show/hide your bar",
                "&f/%label% status &8- &7Show diagnostics",
                "&f/%label% reload &8- &7Reload configuration",
                "&f/%label% update check &8- &7Check GitHub for updates",
                "&f/%label% update download &8- &7Stage the latest update",
                "&8&m--------------------------------"
        ))) {
            if ((line.contains(" reload ") || line.contains(" update "))
                    && !sender.hasPermission(plugin.adminPermission())) {
                continue;
            }
            messages.sendRaw(sender, line, tokens);
        }
    }

    private void status(CommandSender sender) {
        if (!sender.hasPermission(plugin.adminPermission()) && !(sender instanceof Player)) {
            messages.send(sender, "commands.no-permission", "&cYou do not have permission to do that.");
            return;
        }

        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("version", plugin.getDescription().getVersion());
        tokens.put("system", placeholders.system());
        tokens.put("placeholderapi_state", messages.state(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")));
        tokens.put("xenolevels_state", messages.state(Bukkit.getPluginManager().isPluginEnabled("XenoLevels")));

        if (sender instanceof Player player) {
            XenoPlaceholderService.LevelSnapshot data = placeholders.snapshot(player);
            BossBarService.DisplayState displayState = bossBars.displayState(player);

            tokens.put("placeholder_data_state", messages.state(data.valid()));
            tokens.put("level", safe(data.level()));
            tokens.put("exp", safe(data.exp()));
            tokens.put("required", safe(data.required()));
            tokens.put("remaining", safe(data.remaining()));
            tokens.put("percent", safe(data.percent()));
            tokens.put("max_level", safe(data.maxLevel()));
            tokens.put("hud_state", messages.hudState(bossBars.isVisible(player)));
            tokens.put("world", player.getWorld().getName());
            tokens.put("gamemode", player.getGameMode().name());
            tokens.put("hud_reason", messages.reason(displayState.reasonKey(), tokens));
        }

        List<String> lines = new ArrayList<>(messages.getList("status.lines", List.of(
                "&8&m--------------------------------",
                "&dXenoLevelBar &f%version%",
                "&7System: &f%system%",
                "&7PlaceholderAPI: %placeholderapi_state%",
                "&7XenoLevels: %xenolevels_state%"
        )));
        if (sender instanceof Player) {
            lines.addAll(messages.getList("status.player-lines", List.of(
                    "&7Placeholder data: %placeholder_data_state%",
                    "&7Level: &f%level% &8| &7EXP: &f%exp%/%required% &8| &7Progress: &f%percent%%",
                    "&7HUD: %hud_state% &8- &7%hud_reason%"
            )));
        }
        lines.addAll(messages.getList("status.footer", List.of("&8&m--------------------------------")));

        for (String line : lines) {
            messages.sendRaw(sender, line, tokens);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            options.add("toggle");
            options.add("status");
            options.add("help");
            if (sender.hasPermission(plugin.adminPermission())) {
                options.add("reload");
                options.add("update");
            }
            return options.stream().filter(s -> s.startsWith(input)).toList();
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("update")
                && sender.hasPermission(plugin.adminPermission())) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return List.of("check", "download").stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private Map<String, String> updateTokens(UpdateService.UpdateCheck check) {
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("current", check.currentVersion());
        tokens.put("latest", check.latestVersion());
        tokens.put("release_url", check.releaseUrl());
        return tokens;
    }

    private void sendUpdateError(CommandSender sender, Throwable throwable) {
        messages.send(
                sender,
                "commands.update-failed",
                "&cUpdate failed: %error%",
                Map.of("error", rootMessage(throwable))
        );
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }
}
