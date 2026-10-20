package org.lts.anyplugin.perms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.hooks.VaultHook;
import org.lts.anyplugin.player.Node;
import org.lts.anyplugin.player.PlayerData;

public final class PermissionService implements Listener {

    private static final String INTERNAL = "anyplugin.internal.user.";

    public record Resolved(List<Group> groups, Group primary, Map<String, Boolean> nodes, String prefix, String suffix) {
    }

    private final AnyPlugin plugin;
    private final GroupManager groups;
    private final boolean internal;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<UUID, Resolved> cache = new ConcurrentHashMap<>();
    private volatile List<String> registered = List.of();

    public PermissionService(AnyPlugin plugin, boolean internal) {
        this.plugin = plugin;
        this.internal = internal;
        this.groups = new GroupManager(plugin);
    }

    public boolean internal() {
        return internal;
    }

    public GroupManager groups() {
        return groups;
    }

    public void start() {
        groups.load();
        refreshRegistered();
        if (internal) {
            plugin.tasks().asyncTimer(30_000, 30_000, this::expireNodes);
        }
    }

    public void stop() {
        for (Map.Entry<UUID, PermissionAttachment> entry : attachments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                try {
                    player.removeAttachment(entry.getValue());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        attachments.clear();
        synchronized (PermissionService.class) {
            PluginManager pm = Bukkit.getPluginManager();
            for (Permission permission : new ArrayList<>(pm.getPermissions())) {
                if (permission.getName().startsWith(INTERNAL)) {
                    pm.removePermission(permission);
                }
            }
        }
    }

    public void refreshRegistered() {
        List<String> names = new ArrayList<>();
        synchronized (PermissionService.class) {
            for (Permission permission : Bukkit.getPluginManager().getPermissions()) {
                if (!permission.getName().startsWith(INTERNAL)) {
                    names.add(permission.getName().toLowerCase(Locale.ROOT));
                }
            }
        }
        names.sort(null);
        registered = List.copyOf(names);
    }

    public List<String> registeredNodes() {
        return registered;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        attachments.remove(uuid);
        cache.remove(uuid);
        if (internal) {
            synchronized (PermissionService.class) {
                Permission permission = Bukkit.getPluginManager().getPermission(INTERNAL + uuid);
                if (permission != null) {
                    Bukkit.getPluginManager().removePermission(permission);
                }
            }
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        refreshRegistered();
        applyAll();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin() != plugin && Bukkit.getOnlinePlayers().size() > 0) {
            refreshRegistered();
            applyAll();
        }
    }

    public void applyAll() {
        plugin.tasks().forEachOnline(this::apply);
    }

    public void applyLater(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            plugin.tasks().entity(player, () -> apply(player));
        }
    }

    public void apply(Player player) {
        apply(player, true);
    }

    private void apply(Player player, boolean refreshTab) {
        PlayerData data = plugin.players().data(player);
        Resolved resolved = resolve(data);
        cache.put(player.getUniqueId(), resolved);
        if (internal) {
            Map<String, Boolean> children = expand(resolved.nodes());
            synchronized (PermissionService.class) {
                PluginManager pm = Bukkit.getPluginManager();
                String name = INTERNAL + player.getUniqueId();
                Permission permission = pm.getPermission(name);
                if (permission == null) {
                    permission = new Permission(name, PermissionDefault.FALSE, children);
                    pm.addPermission(permission);
                } else {
                    permission.getChildren().clear();
                    permission.getChildren().putAll(children);
                }
                PermissionAttachment attachment = attachments.get(player.getUniqueId());
                if (attachment == null) {
                    attachment = player.addAttachment(plugin);
                    attachments.put(player.getUniqueId(), attachment);
                    attachment.setPermission(permission, true);
                } else {
                    permission.recalculatePermissibles();
                }
            }
            player.updateCommands();
        }
        if (refreshTab && plugin.tab() != null) {
            plugin.tab().refresh(player);
        }
    }

    public Resolved resolved(Player player) {
        Resolved resolved = cache.get(player.getUniqueId());
        if (resolved == null) {
            resolved = resolve(plugin.players().data(player));
            cache.put(player.getUniqueId(), resolved);
        }
        return resolved;
    }

    public Resolved resolve(PlayerData data) {
        if (!internal) {
            return external(data);
        }
        long now = System.currentTimeMillis();
        List<Group> direct = new ArrayList<>();
        direct.add(groups.defaultGroup());
        for (Node node : data.nodes) {
            if (node.isGroup() && node.value() && !node.expired(now)) {
                Group group = groups.get(node.node());
                if (group != null && !direct.contains(group)) {
                    direct.add(group);
                }
            }
        }
        Set<Group> closure = new HashSet<>();
        Deque<Group> stack = new ArrayDeque<>(direct);
        while (!stack.isEmpty()) {
            Group group = stack.pop();
            if (!closure.add(group)) {
                continue;
            }
            for (String parent : group.parents) {
                Group p = groups.get(parent);
                if (p != null && !closure.contains(p)) {
                    stack.push(p);
                }
            }
        }
        List<Group> ordered = new ArrayList<>(closure);
        ordered.sort(Comparator.comparingInt((Group g) -> g.weight).thenComparing(g -> g.name));
        LinkedHashMap<String, Boolean> nodes = new LinkedHashMap<>();
        for (Group group : ordered) {
            group.permissions.forEach((node, value) -> put(nodes, node, value));
        }
        for (Node node : data.nodes) {
            if (!node.isGroup() && !node.expired(now)) {
                put(nodes, node.node(), node.value());
            }
        }
        Group primary = direct.stream().max(Comparator.comparingInt((Group g) -> g.weight)).orElse(groups.defaultGroup());
        String prefix = data.prefix;
        String suffix = data.suffix;
        for (int i = ordered.size() - 1; i >= 0 && (prefix == null || suffix == null); i--) {
            Group group = ordered.get(i);
            if (prefix == null && !group.prefix.isEmpty()) {
                prefix = group.prefix;
            }
            if (suffix == null && !group.suffix.isEmpty()) {
                suffix = group.suffix;
            }
        }
        return new Resolved(ordered, primary, nodes, prefix == null ? "" : prefix, suffix == null ? "" : suffix);
    }

    private Resolved external(PlayerData data) {
        Player player = Bukkit.getPlayer(data.uuid);
        String prefix = data.prefix;
        String suffix = data.suffix;
        String groupName = "default";
        if (player != null && VaultHook.available()) {
            if (prefix == null) {
                prefix = VaultHook.prefix(player);
            }
            if (suffix == null) {
                suffix = VaultHook.suffix(player);
            }
            groupName = VaultHook.primaryGroup(player);
        }
        Group group = new Group(groupName == null ? "default" : groupName.toLowerCase(Locale.ROOT));
        Group configured = groups.get(group.name);
        if (configured != null) {
            group = configured;
        }
        return new Resolved(List.of(group), group, Map.of(), prefix == null ? "" : prefix, suffix == null ? "" : suffix);
    }

    private static void put(LinkedHashMap<String, Boolean> map, String node, boolean value) {
        map.remove(node);
        map.put(node, value);
    }

    private Map<String, Boolean> expand(Map<String, Boolean> nodes) {
        LinkedHashMap<String, Boolean> out = new LinkedHashMap<>();
        List<Map.Entry<String, Boolean>> wildcards = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : nodes.entrySet()) {
            if (entry.getKey().equals("*") || entry.getKey().endsWith(".*")) {
                wildcards.add(entry);
            }
        }
        wildcards.sort(Comparator.comparingInt(e -> depth(e.getKey())));
        List<String> known = registered;
        for (Map.Entry<String, Boolean> wildcard : wildcards) {
            String key = wildcard.getKey();
            String base = key.equals("*") ? "" : key.substring(0, key.length() - 1);
            for (String name : known) {
                if (name.startsWith(base)) {
                    put(out, name, wildcard.getValue());
                }
            }
            put(out, key, wildcard.getValue());
        }
        for (Map.Entry<String, Boolean> entry : nodes.entrySet()) {
            if (!entry.getKey().equals("*") && !entry.getKey().endsWith(".*")) {
                put(out, entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static int depth(String node) {
        if (node.equals("*")) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < node.length(); i++) {
            if (node.charAt(i) == '.') {
                count++;
            }
        }
        return count;
    }

    public boolean hasOffline(UUID uuid, PlayerData data, String node) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.isOp()) {
            return true;
        }
        Map<String, Boolean> effective = new HashMap<>();
        if (internal) {
            for (Map.Entry<String, Boolean> entry : expand(resolve(data).nodes()).entrySet()) {
                walk(entry.getKey(), entry.getValue(), effective, 0);
            }
        }
        Boolean value = effective.get(node);
        if (value != null) {
            return value;
        }
        Permission permission = Bukkit.getPluginManager().getPermission(node);
        return permission != null && permission.getDefault() == PermissionDefault.TRUE;
    }

    private void walk(String name, boolean value, Map<String, Boolean> out, int depth) {
        out.put(name, value);
        if (depth > 16) {
            return;
        }
        Permission permission = Bukkit.getPluginManager().getPermission(name);
        if (permission == null) {
            return;
        }
        for (Map.Entry<String, Boolean> child : permission.getChildren().entrySet()) {
            walk(child.getKey(), child.getValue() == value, out, depth + 1);
        }
    }

    public int limit(Player player, String prefix, int fallback) {
        int best = -1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String node = info.getPermission().toLowerCase(Locale.ROOT);
            if (!node.startsWith(prefix)) {
                continue;
            }
            String tail = node.substring(prefix.length());
            if (tail.equals("unlimited") || tail.equals("*")) {
                return Integer.MAX_VALUE;
            }
            try {
                best = Math.max(best, Integer.parseInt(tail));
            } catch (NumberFormatException ignored) {
            }
        }
        return best < 0 ? fallback : best;
    }

    public String prefix(Player player) {
        return resolved(player).prefix();
    }

    public String suffix(Player player) {
        return resolved(player).suffix();
    }

    public Group primary(Player player) {
        return resolved(player).primary();
    }

    public List<String> groupNames(PlayerData data) {
        List<String> names = new ArrayList<>();
        names.add(groups.defaultGroup().name);
        long now = System.currentTimeMillis();
        for (Node node : data.nodes) {
            if (node.isGroup() && node.value() && !node.expired(now) && !names.contains(node.node())) {
                names.add(node.node());
            }
        }
        return names;
    }

    public void setNode(UUID uuid, Node node) {
        PlayerData data = plugin.players().data(uuid);
        if (data != null) {
            data.nodes.removeIf(n -> n.kind().equals(node.kind()) && n.node().equals(node.node()));
            data.nodes.add(node);
        }
        plugin.db().update("INSERT OR REPLACE INTO user_nodes (uuid, kind, node, value, expires) VALUES (?,?,?,?,?)",
                uuid.toString(), node.kind(), node.node(), node.value(), node.expires()).thenRun(() -> applyLater(uuid));
    }

    public void unsetNode(UUID uuid, String kind, String node) {
        PlayerData data = plugin.players().data(uuid);
        if (data != null) {
            data.nodes.removeIf(n -> n.kind().equals(kind) && n.node().equals(node));
        }
        plugin.db().update("DELETE FROM user_nodes WHERE uuid=? AND kind=? AND node=?", uuid.toString(), kind, node)
                .thenRun(() -> applyLater(uuid));
    }

    private void expireNodes() {
        long now = System.currentTimeMillis();
        for (PlayerData data : plugin.players().all()) {
            boolean changed = data.nodes.removeIf(node -> node.expired(now));
            if (changed) {
                plugin.db().update("DELETE FROM user_nodes WHERE uuid=? AND expires IS NOT NULL AND expires<=?", data.uuid.toString(), now);
                applyLater(data.uuid);
            }
        }
    }
}
