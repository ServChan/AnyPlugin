package org.lts.anyplugin.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.lts.anyplugin.admin.AdminSettings.Setting;

class AdminSettingsTest {

    private static Object parse(String type, String json) {
        return AdminSettings.parse(new Setting("key", type, false), JsonParser.parseString(json));
    }

    private static Map<String, Setting> bundled() throws Exception {
        try (Reader reader = new InputStreamReader(AdminSettingsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            return AdminSettings.of(yaml).stream().collect(Collectors.toMap(Setting::key, Function.identity()));
        }
    }

    @Test
    void typesFollowTheDefaultValue() {
        assertEquals("bool", AdminSettings.type(true));
        assertEquals("int", AdminSettings.type(5));
        assertEquals("int", AdminSettings.type(5L));
        assertEquals("double", AdminSettings.type(1.5));
        assertEquals("double", AdminSettings.type(1.5f));
        assertEquals("list", AdminSettings.type(List.of("a")));
        assertEquals("string", AdminSettings.type("ru"));
        assertEquals("string", AdminSettings.type(null));
    }

    @Test
    void parsesEachType() {
        assertEquals(true, parse("bool", "true"));
        assertEquals(42, parse("int", "42"));
        assertEquals(42, parse("int", "'42'"));
        assertEquals(0.25, parse("double", "0.25"));
        assertEquals("en", parse("string", "'en'"));
        assertEquals(List.of("a", "b c"), parse("list", "[' a ', '', 'b c', '   ']"));
    }

    @Test
    void listDropsOversizeItems() {
        assertEquals(List.of("ok"), parse("list", "['" + "x".repeat(201) + "', 'ok']"));
        assertEquals(List.of("x".repeat(200)), parse("list", "['" + "x".repeat(200) + "']"));
    }

    @Test
    void rejectsValuesOfTheWrongType() {
        assertThrows(NumberFormatException.class, () -> parse("int", "'ten'"));
        assertThrows(IllegalStateException.class, () -> parse("int", "[1, 2]"));
        assertThrows(NumberFormatException.class, () -> parse("double", "'fast'"));
        assertThrows(IllegalStateException.class, () -> parse("list", "'a,b'"));
        assertThrows(IllegalStateException.class, () -> parse("string", "['en', 'ru']"));
    }

    @Test
    void stringRejectsNewlinesAndOversizeText() {
        assertThrows(IllegalArgumentException.class, () -> parse("string", "'a\\nb'"));
        assertThrows(IllegalArgumentException.class, () -> parse("string", "'" + "x".repeat(401) + "'"));
        assertEquals("x".repeat(400), parse("string", "'" + "x".repeat(400) + "'"));
    }

    @Test
    void bundledConfigSettings() throws Exception {
        Map<String, Setting> settings = bundled();
        assertEquals("string", settings.get("language").type());
        assertFalse(settings.get("language").restart());
        assertEquals("bool", settings.get("modules.permissions").type());
        assertTrue(settings.get("modules.permissions").restart());
        assertEquals("int", settings.get("tab.update-ticks").type());
        assertTrue(settings.get("tab.update-ticks").restart());
        assertEquals("list", settings.get("restart.warnings").type());
        assertEquals("list", settings.get("moderation.freeze-allowed-commands").type());
    }

    @Test
    void bundledConfigHidesSectionsAndStructuredSettings() throws Exception {
        Map<String, Setting> settings = bundled();
        assertFalse(settings.containsKey("modules"));
        assertFalse(settings.containsKey("teleport.cooldowns"));
        for (String key : settings.keySet()) {
            assertFalse(key.equals("links") || key.startsWith("links."), key);
            assertFalse(key.startsWith("moderation.templates"), key);
            assertFalse(key.startsWith("moderation.warn-actions"), key);
        }
    }

    @Test
    void noDefaultsMeansNoSettings() {
        assertTrue(AdminSettings.of(null).isEmpty());
    }
}
