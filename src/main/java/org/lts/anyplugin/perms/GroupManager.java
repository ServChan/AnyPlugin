package org.lts.anyplugin.perms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.lts.anyplugin.AnyPlugin;

public final class GroupManager {

    private final AnyPlugin plugin;
    private final File file;
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private volatile String defaultGroup = "default";
    private volatile String pendingSave;

    public GroupManager(AnyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "groups.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("groups.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        defaultGroup = yaml.getString("default-group", "default").toLowerCase(Locale.ROOT);
        Map<String, Group> loaded = new TreeMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("groups");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection g = section.getConfigurationSection(key);
                if (g == null) {
                    continue;
                }
                Group group = new Group(key.toLowerCase(Locale.ROOT));
                group.weight = g.getInt("weight", 0);
                group.prefix = g.getString("prefix", "");
                group.suffix = g.getString("suffix", "");
                group.nameColor = g.getString("name-color", "");
                group.chatFormat = g.getString("chat-format", "");
                for (String parent : g.getStringList("parents")) {
                    group.parents.add(parent.toLowerCase(Locale.ROOT));
                }
                for (String node : g.getStringList("permissions")) {
                    putNode(group.permissions, node);
                }
                loaded.put(group.name, group);
            }
        }
        if (!loaded.containsKey(defaultGroup)) {
            loaded.put(defaultGroup, new Group(defaultGroup));
        }
        groups.clear();
        groups.putAll(loaded);
    }

    static void putNode(Map<String, Boolean> map, String raw) {
        String node = raw.trim();
        if (node.isEmpty()) {
            return;
        }
        boolean value = !node.startsWith("-");
        if (!value) {
            node = node.substring(1);
        }
        map.put(node.toLowerCase(Locale.ROOT), value);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("default-group", defaultGroup);
        for (Group group : sorted()) {
            String path = "groups." + group.name + ".";
            yaml.set(path + "weight", group.weight);
            yaml.set(path + "prefix", group.prefix);
            yaml.set(path + "suffix", group.suffix);
            yaml.set(path + "name-color", group.nameColor);
            if (!group.chatFormat.isEmpty()) {
                yaml.set(path + "chat-format", group.chatFormat);
            }
            yaml.set(path + "parents", new ArrayList<>(group.parents));
            List<String> nodes = new ArrayList<>();
            new TreeMap<>(group.permissions).forEach((node, value) -> nodes.add(value ? node : "-" + node));
            yaml.set(path + "permissions", nodes);
        }
        synchronized (this) {
            pendingSave = yaml.saveToString();
        }
        plugin.tasks().async(this::writePending);
    }

    private synchronized void writePending() {
        String data = pendingSave;
        if (data == null) {
            return;
        }
        pendingSave = null;
        try {
            java.nio.file.Files.writeString(file.toPath(), data);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save groups.yml", e);
        }
    }

    public Group get(String name) {
        return name == null ? null : groups.get(name.toLowerCase(Locale.ROOT));
    }

    public Group defaultGroup() {
        Group group = groups.get(defaultGroup);
        if (group == null) {
            group = new Group(defaultGroup);
            groups.put(defaultGroup, group);
        }
        return group;
    }

    public Collection<Group> all() {
        return groups.values();
    }

    public List<Group> sorted() {
        List<Group> list = new ArrayList<>(groups.values());
        list.sort(Comparator.comparingInt((Group g) -> g.weight).reversed().thenComparing(g -> g.name));
        return list;
    }

    public List<String> names() {
        return new ArrayList<>(new TreeMap<>(groups).keySet());
    }

    public Group create(String name) {
        Group group = new Group(name.toLowerCase(Locale.ROOT));
        groups.put(group.name, group);
        return group;
    }

    public boolean delete(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (key.equals(defaultGroup)) {
            return false;
        }
        if (groups.remove(key) == null) {
            return false;
        }
        for (Group group : groups.values()) {
            group.parents.remove(key);
        }
        return true;
    }
}
