package org.lts.anyplugin.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.admin.AdminSettings.Setting;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.core.Tasks;
import org.lts.anyplugin.moderation.Punishment;
import org.lts.anyplugin.perms.Group;
import org.lts.anyplugin.player.Node;
import org.lts.anyplugin.player.PlayerData;
import org.lts.anyplugin.player.PlayerService;
import org.lts.anyplugin.storage.Database;
import org.lts.anyplugin.tab.ServerStats;
import org.lts.anyplugin.teleport.WarpService;

public final class AdminChannel implements PluginMessageListener, Listener {

    public static final String CHANNEL = "anyplugin:admin";
    public static final int PROTOCOL = 1;
    private static final int MAX_INCOMING = 32_768;
    private static final Map<String, String> CAPABILITIES = Map.ofEntries(
            Map.entry("perm", "anyplugin.perm"), Map.entry("manage", "anyplugin.manage"), Map.entry("ban", "anyplugin.ban"),
            Map.entry("ipban", "anyplugin.ipban"), Map.entry("unban", "anyplugin.unban"), Map.entry("mute", "anyplugin.mute"),
            Map.entry("unmute", "anyplugin.unmute"), Map.entry("warn", "anyplugin.warn"), Map.entry("unwarn", "anyplugin.unwarn"),
            Map.entry("kick", "anyplugin.kick"), Map.entry("history", "anyplugin.history"), Map.entry("gamemode", "anyplugin.gamemode.others"),
            Map.entry("heal", "anyplugin.heal.others"), Map.entry("feed", "anyplugin.feed.others"), Map.entry("fly", "anyplugin.fly.others"),
            Map.entry("invsee", "anyplugin.invsee"), Map.entry("enderchest", "anyplugin.enderchest.others"), Map.entry("tphere", "anyplugin.tphere"),
            Map.entry("tpto", "minecraft.command.teleport"), Map.entry("freeze", "anyplugin.freeze"), Map.entry("vanish", "anyplugin.vanish"),
            Map.entry("setwarp", "anyplugin.setwarp"), Map.entry("delwarp", "anyplugin.delwarp"), Map.entry("warp", "anyplugin.warp"),
            Map.entry("maintenance", "anyplugin.maintenance"), Map.entry("restart", "anyplugin.restart"), Map.entry("broadcast", "anyplugin.broadcast"),
            Map.entry("time", "anyplugin.time"), Map.entry("weather", "anyplugin.weather"), Map.entry("whoisip", "anyplugin.whois.ip"));

    private final AnyPlugin plugin;
    private final WarpService warps;
    private final Map<UUID, long[]> rate = new ConcurrentHashMap<>();

    public AdminChannel(AnyPlugin plugin, WarpService warps) {
        this.plugin = plugin;
        this.warps = warps;
    }

    public void start() {
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void stop() {
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
        messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void register(CommandRegistry registry) {
        registry.cmd("apgui").alias("admingui").perm("anyplugin.gui").playerOnly().exec(ctx -> open(ctx.player()));
    }

    public boolean hasMod(Player player) {
        return player.getListeningPluginChannels().contains(CHANNEL);
    }

    public void open(Player player) {
        if (!hasMod(player)) {
            plugin.msg().send(player, "admin.no-mod");
            return;
        }
        sendSnapshot(player, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rate.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!CHANNEL.equals(channel) || data.length > MAX_INCOMING || throttled(player)) {
            return;
        }
        JsonObject message;
        try {
            message = JsonParser.parseString(decode(data)).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        plugin.tasks().entity(player, () -> {
            try {
                handle(player, message);
            } catch (IllegalArgumentException e) {
                error(player, "invalid");
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Admin request from " + player.getName() + " failed", e);
                error(player, "internal");
            }
        });
    }

    private boolean throttled(Player player) {
        long now = System.currentTimeMillis();
        long[] window = rate.computeIfAbsent(player.getUniqueId(), k -> new long[] {now, 0});
        synchronized (window) {
            if (now - window[0] > 1000) {
                window[0] = now;
                window[1] = 0;
            }
            return ++window[1] > 20;
        }
    }

    private void handle(Player player, JsonObject message) {
        if (!player.hasPermission("anyplugin.gui")) {
            JsonObject denied = new JsonObject();
            denied.addProperty("type", "denied");
            send(player, denied);
            return;
        }
        switch (AdminCommands.string(message, "type")) {
            case "hello" -> sendSnapshot(player, message.has("open") && message.get("open").getAsBoolean());
            case "refresh" -> sendSnapshot(player, false);
            case "player" -> sendPlayer(player, AdminCommands.require(message, "name", AdminCommands.NAME));
            case "nodes" -> sendNodes(player);
            case "action" -> action(player, message);
            default -> {
            }
        }
    }

    private void action(Player player, JsonObject message) {
        String op = AdminCommands.string(message, "op");
        if (op.equals("config.set")) {
            setConfig(player, message);
            return;
        }
        String command = AdminCommands.command(op, message);
        if (command == null) {
            error(player, "invalid");
            return;
        }
        player.performCommand(command);
        JsonObject done = new JsonObject();
        done.addProperty("type", "done");
        done.addProperty("op", op);
        send(player, done);
        plugin.tasks().entityLater(player, 10, () -> sendSnapshot(player, false));
    }

    private void setConfig(Player player, JsonObject message) {
        if (!player.hasPermission("anyplugin.manage")) {
            error(player, "denied");
            return;
        }
        String key = AdminCommands.string(message, "key");
        Setting setting = settings().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow(() -> new IllegalArgumentException(key));
        Object value = AdminSettings.parse(setting, message.get("value"));
        plugin.getConfig().set(key, value);
        String yaml = plugin.getConfig().saveToString();
        plugin.tasks().async(() -> {
            synchronized (AdminChannel.class) {
                try {
                    java.nio.file.Files.writeString(new java.io.File(plugin.getDataFolder(), "config.yml").toPath(), yaml, StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not save config.yml", e);
                }
            }
        });
        if (key.equals("language")) {
            plugin.msg().load(String.valueOf(value));
        }
        if (plugin.staff() != null) {
            plugin.staff().log(player, "config " + key + " = " + value);
        }
        JsonObject done = new JsonObject();
        done.addProperty("type", "done");
        done.addProperty("op", "config.set");
        done.addProperty("restart", setting.restart());
        send(player, done);
        sendSnapshot(player, false);
    }

    private List<Setting> settings() {
        return AdminSettings.of(plugin.getConfig().getDefaults());
    }

    public void sendSnapshot(Player player, boolean open) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "snapshot");
        out.addProperty("protocol", PROTOCOL);
        out.addProperty("open", open);
        out.addProperty("self", player.getName());
        out.addProperty("permissionsInternal", plugin.perms().internal());

        JsonObject can = new JsonObject();
        CAPABILITIES.forEach((name, node) -> can.addProperty(name, player.hasPermission(node)));
        out.add("can", can);

        JsonObject server = new JsonObject();
        server.addProperty("software", Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
        server.addProperty("folia", Tasks.FOLIA);
        server.addProperty("tps", ServerStats.tps());
        server.addProperty("mspt", ServerStats.mspt());
        server.addProperty("online", Bukkit.getOnlinePlayers().size());
        server.addProperty("max", Bukkit.getMaxPlayers());
        server.addProperty("unique", plugin.players().uniquePlayers());
        server.addProperty("uptime", ServerStats.uptimeMillis());
        Runtime runtime = Runtime.getRuntime();
        server.addProperty("memoryUsed", (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576);
        server.addProperty("memoryMax", runtime.maxMemory() / 1_048_576);
        server.addProperty("maintenance", plugin.maintenance() != null && plugin.maintenance().enabled());
        server.addProperty("maintenanceModule", plugin.maintenance() != null);
        server.addProperty("restartIn", plugin.restart() == null ? -1 : plugin.restart().remaining());
        out.add("server", server);

        out.addProperty("defaultGroup", plugin.perms().groups().defaultGroup().name);
        JsonArray groups = new JsonArray();
        for (Group group : plugin.perms().groups().sorted()) {
            JsonObject g = new JsonObject();
            g.addProperty("name", group.name);
            g.addProperty("weight", group.weight);
            g.addProperty("prefix", group.prefix);
            g.addProperty("suffix", group.suffix);
            g.addProperty("color", group.nameColor);
            JsonArray parents = new JsonArray();
            group.parents.forEach(parents::add);
            g.add("parents", parents);
            JsonObject permissions = new JsonObject();
            new java.util.TreeMap<>(group.permissions).forEach(permissions::addProperty);
            g.add("permissions", permissions);
            groups.add(g);
        }
        out.add("groups", groups);

        JsonArray online = new JsonArray();
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (Player other : players) {
            PlayerData data = plugin.players().data(other.getUniqueId());
            JsonObject p = new JsonObject();
            p.addProperty("name", other.getName());
            p.addProperty("uuid", other.getUniqueId().toString());
            p.addProperty("group", plugin.perms().primary(other).name);
            p.addProperty("prefix", plugin.perms().prefix(other));
            p.addProperty("world", other.getWorld().getName());
            p.addProperty("gamemode", other.getGameMode().name().toLowerCase(Locale.ROOT));
            p.addProperty("ping", other.getPing());
            p.addProperty("op", other.isOp());
            p.addProperty("afk", data != null && data.afk);
            p.addProperty("vanished", data != null && data.vanished);
            p.addProperty("muted", data != null && data.muted());
            p.addProperty("frozen", data != null && data.frozen);
            online.add(p);
        }
        out.add("online", online);

        JsonArray warpList = new JsonArray();
        if (warps != null) {
            warps.all().stream().sorted((a, b) -> a.name().compareTo(b.name())).forEach(warp -> {
                JsonObject w = new JsonObject();
                w.addProperty("name", warp.name());
                w.addProperty("world", warp.world());
                w.addProperty("x", (int) Math.floor(warp.x()));
                w.addProperty("y", (int) Math.floor(warp.y()));
                w.addProperty("z", (int) Math.floor(warp.z()));
                w.addProperty("icon", warp.icon() == null ? "" : warp.icon());
                w.addProperty("description", warp.description() == null ? "" : warp.description());
                warpList.add(w);
            });
        }
        out.add("warps", warpList);
        out.addProperty("warpsModule", warps != null);

        JsonArray settings = new JsonArray();
        JsonObject sections = new JsonObject();
        if (player.hasPermission("anyplugin.manage")) {
            for (Setting setting : settings()) {
                JsonObject s = new JsonObject();
                s.addProperty("key", setting.key());
                s.addProperty("type", setting.type());
                s.addProperty("restart", setting.restart());
                String labelKey = "setting-labels." + setting.key();
                s.addProperty("label", plugin.msg().has(labelKey) ? plugin.msg().raw(labelKey) : setting.key());
                int dot = setting.key().indexOf('.');
                String section = dot < 0 ? setting.key() : setting.key().substring(0, dot);
                s.addProperty("section", section);
                String sectionKey = "setting-sections." + section;
                sections.addProperty(section, plugin.msg().has(sectionKey) ? plugin.msg().raw(sectionKey) : section);
                Object value = plugin.getConfig().get(setting.key());
                if (value instanceof List<?> list) {
                    JsonArray array = new JsonArray();
                    list.forEach(item -> array.add(String.valueOf(item)));
                    s.add("value", array);
                } else if (value instanceof Boolean b) {
                    s.addProperty("value", b);
                } else if (value instanceof Number n) {
                    s.addProperty("value", n);
                } else {
                    s.addProperty("value", value == null ? "" : String.valueOf(value));
                }
                settings.add(s);
            }
        }
        out.add("settings", settings);
        out.add("settingSections", sections);
        send(player, out);
    }

    private void sendNodes(Player player) {
        java.util.TreeSet<String> nodes = new java.util.TreeSet<>(plugin.perms().registeredNodes());
        for (Group group : plugin.perms().groups().all()) {
            nodes.addAll(group.permissions.keySet());
        }
        JsonArray list = new JsonArray();
        nodes.forEach(list::add);
        JsonObject out = new JsonObject();
        out.addProperty("type", "nodes");
        out.add("list", list);
        send(player, out);
    }

    private record Row(String nick, String prefix, String suffix) {
    }

    private void sendPlayer(Player viewer, String name) {
        boolean showIp = viewer.hasPermission("anyplugin.whois.ip");
        plugin.players().lookup(name).thenAccept(found -> {
            if (found.isEmpty()) {
                JsonObject out = new JsonObject();
                out.addProperty("type", "player");
                out.addProperty("found", false);
                out.addProperty("name", name);
                send(viewer, out);
                return;
            }
            UUID uuid = found.get().uuid();
            plugin.db().call(c -> {
                JsonObject out = new JsonObject();
                out.addProperty("type", "player");
                out.addProperty("found", true);
                out.addProperty("name", found.get().name());
                out.addProperty("uuid", uuid.toString());
                out.addProperty("firstJoin", found.get().firstJoin());
                out.addProperty("lastSeen", found.get().lastSeen());
                out.addProperty("playtime", found.get().playtime());
                out.addProperty("ip", showIp && found.get().lastIp() != null ? found.get().lastIp() : "");
                List<Row> rows = Database.list(c, "SELECT nick, prefix, suffix FROM players WHERE uuid=?",
                        rs -> new Row(rs.getString("nick"), rs.getString("prefix"), rs.getString("suffix")), uuid.toString());
                Row row = rows.isEmpty() ? new Row(null, null, null) : rows.get(0);
                out.addProperty("nick", row.nick() == null ? "" : row.nick());
                out.addProperty("prefix", row.prefix() == null ? "" : row.prefix());
                out.addProperty("suffix", row.suffix() == null ? "" : row.suffix());
                long now = System.currentTimeMillis();
                JsonArray groups = new JsonArray();
                JsonArray nodes = new JsonArray();
                for (Node node : PlayerService.loadNodes(c, uuid)) {
                    if (node.expired(now)) {
                        continue;
                    }
                    JsonObject n = new JsonObject();
                    n.addProperty("node", node.node());
                    n.addProperty("value", node.value());
                    n.addProperty("expires", node.expires() == null ? -1 : node.expires());
                    (node.isGroup() ? groups : nodes).add(n);
                }
                out.add("groups", groups);
                out.add("nodes", nodes);
                JsonArray punishments = new JsonArray();
                for (Punishment item : Database.list(c, "SELECT * FROM punishments WHERE uuid=? ORDER BY created DESC LIMIT 25",
                        Punishment::read, uuid.toString())) {
                    JsonObject p = new JsonObject();
                    p.addProperty("id", item.id());
                    p.addProperty("kind", item.type().key());
                    p.addProperty("reason", item.reason() == null ? "" : item.reason());
                    p.addProperty("actor", item.actor());
                    p.addProperty("created", item.created());
                    p.addProperty("expires", item.expires() == null ? -1 : item.expires());
                    p.addProperty("active", item.type() != Punishment.Type.KICK && item.inEffect(now) && item.removedBy() == null);
                    punishments.add(p);
                }
                out.add("punishments", punishments);
                JsonArray homes = new JsonArray();
                for (String home : Database.list(c, "SELECT name FROM homes WHERE uuid=? ORDER BY name", rs -> rs.getString("name"), uuid.toString())) {
                    homes.add(home);
                }
                out.add("homes", homes);
                return out;
            }).thenAccept(out -> {
                Player target = Bukkit.getPlayer(uuid);
                if (target == null) {
                    out.addProperty("online", false);
                    send(viewer, out);
                    return;
                }
                plugin.tasks().entity(target, () -> {
                    PlayerData data = plugin.players().data(target);
                    Location l = target.getLocation();
                    out.addProperty("online", true);
                    out.addProperty("world", l.getWorld().getName());
                    out.addProperty("x", l.getBlockX());
                    out.addProperty("y", l.getBlockY());
                    out.addProperty("z", l.getBlockZ());
                    out.addProperty("gamemode", target.getGameMode().name().toLowerCase(Locale.ROOT));
                    out.addProperty("health", (int) Math.ceil(target.getHealth()));
                    out.addProperty("food", target.getFoodLevel());
                    out.addProperty("ping", target.getPing());
                    out.addProperty("fly", target.getAllowFlight());
                    out.addProperty("god", data.god);
                    out.addProperty("afk", data.afk);
                    out.addProperty("vanished", data.vanished);
                    out.addProperty("muted", data.muted());
                    out.addProperty("frozen", data.frozen);
                    out.addProperty("group", plugin.perms().primary(target).name);
                    send(viewer, out);
                });
            }).exceptionally(e -> {
                plugin.getLogger().log(Level.WARNING, "Admin player lookup failed", e);
                error(viewer, "internal");
                return null;
            });
        });
    }

    private void error(Player player, String key) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "error");
        out.addProperty("key", key);
        send(player, out);
    }

    private void send(Player player, JsonObject message) {
        if (!player.isOnline()) {
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, encode(message.toString()));
    }

    static byte[] encode(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    static String decode(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }
}
