package org.lts.anyplugin.core;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private volatile YamlConfiguration file = new YamlConfiguration();
    private volatile TagResolver prefix = TagResolver.empty();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        String name = "messages_" + language + ".yml";
        if (plugin.getResource(name) == null) {
            plugin.getLogger().warning("Unknown language " + language + ", using ru");
            name = "messages_ru.yml";
        }
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target);
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled " + name, e);
        }
        file = loaded;
        prefix = Placeholder.parsed("pfx", raw("prefix"));
    }

    public String raw(String key) {
        String value = file.getString(key);
        return value == null ? key : value;
    }

    public List<String> rawList(String key) {
        List<String> list = file.getStringList(key);
        if (list.isEmpty() && file.isString(key)) {
            return List.of(raw(key));
        }
        return list;
    }

    public boolean has(String key) {
        return file.contains(key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return parse(raw(key), resolvers);
    }

    public Component parse(String text, TagResolver... resolvers) {
        return mini.deserialize(Text.legacyToMini(text), TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }

    public List<Component> list(String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) {
            out.add(parse(line, resolvers));
        }
        return out;
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        String text = raw(key);
        if (text.isEmpty()) {
            return;
        }
        audience.sendMessage(parse(text, resolvers));
    }

    public void sendList(Audience audience, String key, TagResolver... resolvers) {
        for (Component line : list(key, resolvers)) {
            audience.sendMessage(line);
        }
    }

    public Component button(String key, ClickEvent click, String hoverKey, TagResolver... resolvers) {
        Component button = get(key, resolvers).clickEvent(click);
        if (hoverKey != null) {
            button = button.hoverEvent(HoverEvent.showText(get(hoverKey, resolvers)));
        }
        return button;
    }

    public String duration(long millis) {
        if (millis < 0) {
            return raw("time.forever");
        }
        long seconds = Math.max(0, (millis + 500) / 1000);
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        long secs = seconds % 60;
        StringBuilder out = new StringBuilder();
        append(out, days, "time.days");
        append(out, hours, "time.hours");
        append(out, minutes, "time.minutes");
        if (days == 0 && hours == 0) {
            append(out, secs, "time.seconds");
        }
        if (out.isEmpty()) {
            return "0" + raw("time.seconds");
        }
        return out.toString().trim();
    }

    private void append(StringBuilder out, long value, String unitKey) {
        if (value > 0) {
            out.append(value).append(raw(unitKey)).append(' ');
        }
    }

    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver c(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public static TagResolver mm(String key, String value) {
        return Placeholder.parsed(key, Text.legacyToMini(value == null ? "" : value));
    }
}
