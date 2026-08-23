package com.xenolevelbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFormatterTest {

    @Test
    void supportsMiniMessage() {
        assertEquals("§cHello", TextFormatter.serializeLegacy("<red>Hello</red>"));
    }

    @Test
    void retainsAmpersandAndSectionColorCodes() {
        assertEquals("§aGreen", TextFormatter.serializeLegacy("&aGreen"));
        assertEquals("§bAqua", TextFormatter.serializeLegacy("§bAqua"));
    }

    @Test
    void supportsLegacyHexColors() {
        String formatted = TextFormatter.serializeLegacy("&#D14CFFHex");

        assertTrue(formatted.startsWith("§x§d§1§4§c§f§f"));
        assertTrue(formatted.endsWith("Hex"));
    }

    @Test
    void supportsMiniMessageAndLegacyCodesTogether() {
        String formatted = TextFormatter.serializeLegacy("<bold>Bold &aGreen</bold>");

        assertTrue(formatted.startsWith("§lBold "));
        assertTrue(formatted.contains("§a"));
        assertTrue(formatted.endsWith("Green"));
    }
}
