package org.lts.anyplugin.admin;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.Configuration;

final class AdminSettings {

    private static final Set<String> SKIPPED = Set.of("moderation.templates", "moderation.warn-actions", "links");
    private static final Set<String> RESTART = Set.of("tab.update-ticks", "announcer.enabled", "announcer.interval-seconds",
            "restart.daily", "nametags.enabled");

    record Setting(String key, String type, boolean restart) {
    }

    private AdminSettings() {
    }

    static List<Setting> of(Configuration defaults) {
        List<Setting> out = new ArrayList<>();
        if (defaults == null) {
            return out;
        }
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || SKIPPED.stream().anyMatch(skip -> key.equals(skip) || key.startsWith(skip + "."))) {
                continue;
            }
            out.add(new Setting(key, type(defaults.get(key)), key.startsWith("modules.") || RESTART.contains(key)));
        }
        return out;
    }

    static String type(Object value) {
        if (value instanceof Boolean) {
            return "bool";
        }
        if (value instanceof Integer || value instanceof Long) {
            return "int";
        }
        if (value instanceof Double || value instanceof Float) {
            return "double";
        }
        if (value instanceof List<?>) {
            return "list";
        }
        return "string";
    }

    static Object parse(Setting setting, JsonElement raw) {
        return switch (setting.type()) {
            case "bool" -> raw.getAsBoolean();
            case "int" -> raw.getAsInt();
            case "double" -> raw.getAsDouble();
            case "list" -> {
                List<String> list = new ArrayList<>();
                for (JsonElement element : raw.getAsJsonArray()) {
                    String item = element.getAsString().strip();
                    if (!item.isEmpty() && item.length() <= 200) {
                        list.add(item);
                    }
                }
                yield list;
            }
            default -> {
                String text = raw.getAsString();
                if (text.length() > 400 || text.indexOf('\n') >= 0) {
                    throw new IllegalArgumentException(setting.key());
                }
                yield text;
            }
        };
    }
}
