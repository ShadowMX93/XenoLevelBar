package com.xenolevelbar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class TextFormatter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.builder()
            .character('\u00a7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Pattern LEGACY_CODE = Pattern.compile(
            "(?i)[&\u00a7](?:#[0-9a-f]{6}|[0-9a-fk-orx])"
    );
    private static final Pattern SECTION_CODE = Pattern.compile(
            "(?i)\u00a7(?=(?:#[0-9a-f]{6}|[0-9a-fk-orx]))"
    );

    private TextFormatter() {
    }

    static Component deserialize(String input) {
        String text = input == null ? "" : input;
        try {
            return deserializeLegacyText(MINI_MESSAGE.deserialize(text));
        } catch (RuntimeException ignored) {
            // A malformed MiniMessage tag should not break the HUD update task or
            // prevent a command reply. Preserve the text and still apply legacy codes.
            return deserializeLegacy(text);
        }
    }

    static String serializeLegacy(String input) {
        return LEGACY_SECTION.serialize(deserialize(input));
    }

    private static Component deserializeLegacyText(Component component) {
        List<Component> children = new ArrayList<>();
        Component result = component.children(List.of());

        if (component instanceof TextComponent text && LEGACY_CODE.matcher(text.content()).find()) {
            result = text.content("").children(List.of());
            children.add(deserializeLegacy(text.content()));
        }

        for (Component child : component.children()) {
            children.add(deserializeLegacyText(child));
        }
        return result.children(children);
    }

    private static Component deserializeLegacy(String input) {
        String normalized = SECTION_CODE.matcher(input).replaceAll("&");
        return LEGACY_AMPERSAND.deserialize(normalized);
    }
}
