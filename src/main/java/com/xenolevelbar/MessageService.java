package com.xenolevelbar;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageService {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");

    private final XenoLevelBarPlugin plugin;
    private final File file;
    private YamlConfiguration messages;

    public MessageService(XenoLevelBarPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        reload();
    }

    public void reload() {
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path, String fallback) {
        return messages.getString(path, fallback);
    }

    public List<String> getList(String path, List<String> fallback) {
        List<String> configured = messages.getStringList(path);
        return configured.isEmpty() ? fallback : configured;
    }

    public void send(CommandSender sender, String path, String fallback) {
        send(sender, path, fallback, Collections.emptyMap(), true);
    }

    public void send(CommandSender sender, String path, String fallback, Map<String, String> tokens) {
        send(sender, path, fallback, tokens, true);
    }

    public void send(CommandSender sender, String path, String fallback, Map<String, String> tokens, boolean withPrefix) {
        String message = get(path, fallback);
        if (message == null || message.isEmpty()) {
            return;
        }
        String prefix = withPrefix ? get("prefix", "&8[&dXenoLevelBar&8] &7") : "";
        sender.sendMessage(format(sender, prefix + message, tokens));
    }

    public void sendRaw(CommandSender sender, String text, Map<String, String> tokens) {
        if (text == null || text.isEmpty()) {
            return;
        }
        sender.sendMessage(format(sender, text, tokens));
    }

    public String state(boolean value) {
        return value
                ? get("states.ok", "&aOK")
                : get("states.no", "&cNO");
    }

    public String hudState(boolean visible) {
        return visible
                ? get("states.shown", "&aSHOWN")
                : get("states.hidden", "&cHIDDEN");
    }

    public String reason(String key, Map<String, String> tokens) {
        String fallback = switch (key) {
            case "shown" -> "&aVisible";
            case "waiting-update" -> "&eEligible; waiting for the next HUD update";
            case "plugin-disabled" -> "&cThe plugin is disabled in config.yml";
            case "missing-permission" -> "&cMissing the HUD permission";
            case "player-toggle" -> "&cDisabled with /xlb toggle";
            case "world-not-enabled" -> "&cThis world is not in visibility.enabled-worlds";
            case "disabled-world" -> "&cThis world is disabled";
            case "gamemode" -> "&cGamemode %gamemode% is not allowed";
            case "invalid-data" -> "&cXenoLevels placeholders could not be resolved";
            case "xenolevels-user-unavailable" -> "&eXenoLevels user data is temporarily unavailable; retrying";
            case "max-level" -> "&eHidden because maximum level was reached";
            default -> get("reasons.unknown", "&7Unknown");
        };
        return replaceTokens(get("reasons." + key, fallback), tokens);
    }

    public String format(CommandSender sender, String text, Map<String, String> tokens) {
        String result = replaceTokens(text, tokens);
        if (sender instanceof Player player) {
            try {
                result = PlaceholderAPI.setPlaceholders(player, result);
            } catch (RuntimeException ignored) {
                // A third-party PlaceholderAPI expansion can temporarily be unavailable
                // (for example XenoLevels immediately after /xlv delete). Command output
                // should still be sent instead of propagating that exception.
            }
        }
        return color(result);
    }

    private static String replaceTokens(String text, Map<String, String> tokens) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "?" : entry.getValue());
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    public static String color(String input) {
        String text = input == null ? "" : input;
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
