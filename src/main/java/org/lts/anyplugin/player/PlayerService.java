package org.lts.anyplugin.player;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.core.Text;
import org.lts.anyplugin.moderation.Punishment;
import org.lts.anyplugin.storage.Database;

public final class PlayerService implements Listener {

    private static final Set<String> FLAGS = Set.of("vanished", "god", "fly", "tptoggle", "msgtoggle", "socialspy", "commandspy", "kit_received");

    private final AnyPlugin plugin;
    private final Map<UUID, PlayerData> online = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData> preloaded = new ConcurrentHashMap<>();
    private final AtomicInteger uniquePlayers = new AtomicInteger();

    public PlayerService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.db().one("SELECT COUNT(*) AS c FROM players", rs -> rs.getInt("c"))
                .thenAccept(count -> uniquePlayers.set(count.orElse(0)));
        plugin.tasks().asyncTimer(300_000, 300_000, this::flushPlaytime);
    }

    public int uniquePlayers() {
        return uniquePlayers.get();
    }

    public PlayerData data(Player player) {
        PlayerData data = online.get(player.getUniqueId());
        if (data == null) {
            data = new PlayerData(player.getUniqueId(), player.getName());
            data.sessionStart = System.currentTimeMillis();
            PlayerData existing = online.putIfAbsent(player.getUniqueId(), data);
            if (existing != null) {
                return existing;
            }
        }
        return data;
    }

    public PlayerData data(UUID uuid) {
        return online.get(uuid);
    }

    public Collection<PlayerData> all() {
        return online.values();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        InetAddress address = event.getAddress();
        String ip = address == null ? null : address.getHostAddress();
        PlayerData data;
        try {
            data = load(uuid, event.getName(), ip).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load data of " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.msg().get("errors.load-failed"));
            return;
        }
        if (plugin.punishments() != null) {
            Component screen = plugin.punishments().loginBlock(uuid, ip);
            if (screen != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, screen);
                return;
            }
        }
        if (plugin.maintenance() != null && plugin.maintenance().enabled() && !plugin.maintenance().canJoin(uuid, data)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, plugin.msg().get("maintenance.kick"));
            return;
        }
        preloaded.put(uuid, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            preloaded.remove(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoinFirst(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData loaded = preloaded.remove(uuid);
        if (loaded != null) {
            online.put(uuid, loaded);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = data(player);
        if (data.isNew && player.hasPlayedBefore()) {
            data.isNew = false;
        }
        data.sessionStart = System.currentTimeMillis();
        data.lastActivity = data.sessionStart;
        applyDisplayName(player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = online.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (data.sessionStart > 0) {
            data.playtime += now - data.sessionStart;
            data.sessionStart = 0;
        }
        data.lastSeen = now;
        PlayerData next = preloaded.get(data.uuid);
        if (next != null) {
            next.playtime = data.playtime;
        }
        plugin.db().update("UPDATE players SET playtime=?, last_seen=? WHERE uuid=?", data.playtime, now, data.uuid.toString())
                .whenComplete((r, e) -> online.remove(data.uuid, data));
    }

    public void adoptOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
            load(player.getUniqueId(), player.getName(), ip).thenAccept(data -> {
                data.isNew = false;
                data.sessionStart = System.currentTimeMillis();
                online.put(player.getUniqueId(), data);
                plugin.tasks().entity(player, () -> {
                    applyDisplayName(player, data);
                    plugin.perms().apply(player);
                });
            });
        }
    }

    public void saveAllNow() {
        long now = System.currentTimeMillis();
        for (PlayerData data : online.values()) {
            long total = data.totalPlaytime();
            plugin.db().update("UPDATE players SET playtime=?, last_seen=? WHERE uuid=?", total, now, data.uuid.toString());
        }
    }

    private void flushPlaytime() {
        long now = System.currentTimeMillis();
        for (PlayerData data : online.values()) {
            if (data.sessionStart > 0) {
                plugin.db().update("UPDATE players SET playtime=?, last_seen=? WHERE uuid=?", data.totalPlaytime(), now, data.uuid.toString());
            }
        }
    }

    private CompletableFuture<PlayerData> load(UUID uuid, String name, String ip) {
        return plugin.db().call(c -> {
            long now = System.currentTimeMillis();
            PlayerData data = new PlayerData(uuid, name);
            String id = uuid.toString();
            List<Boolean> found = Database.list(c, "SELECT * FROM players WHERE uuid=?", rs -> {
                data.nick = rs.getString("nick");
                data.prefix = rs.getString("prefix");
                data.suffix = rs.getString("suffix");
                data.firstJoin = rs.getLong("first_join");
                data.lastSeen = rs.getLong("last_seen");
                data.playtime = rs.getLong("playtime");
                data.vanished = rs.getInt("vanished") == 1;
                data.god = rs.getInt("god") == 1;
                data.fly = rs.getInt("fly") == 1;
                data.tpToggle = rs.getInt("tptoggle") == 1;
                data.msgToggle = rs.getInt("msgtoggle") == 1;
                data.socialSpy = rs.getInt("socialspy") == 1;
                data.commandSpy = rs.getInt("commandspy") == 1;
                data.kitReceived = rs.getInt("kit_received") == 1;
                return true;
            }, id);
            if (found.isEmpty()) {
                data.isNew = true;
                data.firstJoin = now;
                data.lastSeen = now;
                Database.update(c, "INSERT INTO players (uuid, name, name_lower, first_join, last_seen, last_ip) VALUES (?,?,?,?,?,?)",
                        id, name, name.toLowerCase(Locale.ROOT), now, now, ip);
                uniquePlayers.incrementAndGet();
            } else {
                Database.update(c, "UPDATE players SET name=?, name_lower=?, last_ip=? WHERE uuid=?", name, name.toLowerCase(Locale.ROOT), ip, id);
            }
            data.lastIp = ip;
            loadHomes(c, data);
            for (String ignored : Database.list(c, "SELECT ignored FROM ignores WHERE uuid=?", rs -> rs.getString("ignored"), id)) {
                data.ignores.add(UUID.fromString(ignored));
            }
            Database.update(c, "DELETE FROM user_nodes WHERE uuid=? AND expires IS NOT NULL AND expires<=?", id, now);
            data.nodes.addAll(loadNodes(c, uuid));
            List<Punishment> mutes = Database.list(c,
                    "SELECT * FROM punishments WHERE uuid=? AND type='mute' AND active=1 AND (expires IS NULL OR expires>?) ORDER BY created DESC LIMIT 1",
                    Punishment::read, id, now);
            data.mute = mutes.isEmpty() ? null : mutes.get(0);
            return data;
        });
    }

    private static void loadHomes(Connection c, PlayerData data) throws SQLException {
        for (Home home : Database.list(c, "SELECT * FROM homes WHERE uuid=?", rs -> new Home(rs.getString("name"), rs.getString("world"),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")), data.uuid.toString())) {
            data.homes.put(home.name().toLowerCase(Locale.ROOT), home);
        }
    }

    public static List<Node> loadNodes(Connection c, UUID uuid) throws SQLException {
        return Database.list(c, "SELECT * FROM user_nodes WHERE uuid=?",
                rs -> new Node(rs.getString("kind"), rs.getString("node"), rs.getInt("value") == 1, Database.nullableLong(rs, "expires")),
                uuid.toString());
    }

    public CompletableFuture<Optional<OfflineRecord>> lookup(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            PlayerData data = data(player);
            return CompletableFuture.completedFuture(Optional.of(new OfflineRecord(data.uuid, player.getName(), data.nick, data.firstJoin,
                    System.currentTimeMillis(), data.totalPlaytime(), data.lastIp)));
        }
        return plugin.db().one("SELECT * FROM players WHERE name_lower=? ORDER BY last_seen DESC LIMIT 1", rs -> new OfflineRecord(
                UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getString("nick"), rs.getLong("first_join"),
                rs.getLong("last_seen"), rs.getLong("playtime"), rs.getString("last_ip")), name.toLowerCase(Locale.ROOT));
    }

    public CompletableFuture<List<OfflineRecord>> topPlaytime(int limit) {
        saveAllNow();
        return plugin.db().list("SELECT * FROM players ORDER BY playtime DESC LIMIT ?", rs -> new OfflineRecord(
                UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getString("nick"), rs.getLong("first_join"),
                rs.getLong("last_seen"), rs.getLong("playtime"), rs.getString("last_ip")), limit);
    }

    public void setFlag(PlayerData data, String column, boolean value) {
        if (!FLAGS.contains(column)) {
            throw new IllegalArgumentException(column);
        }
        plugin.db().update("UPDATE players SET " + column + "=? WHERE uuid=?", value, data.uuid.toString());
    }

    public void setText(UUID uuid, String column, String value) {
        if (!Set.of("nick", "prefix", "suffix").contains(column)) {
            throw new IllegalArgumentException(column);
        }
        plugin.db().update("UPDATE players SET " + column + "=? WHERE uuid=?", value, uuid.toString());
    }

    public void applyDisplayName(Player player, PlayerData data) {
        if (data.nick == null || data.nick.isEmpty()) {
            player.displayName(null);
        } else {
            player.displayName(Text.userInput(data.nick, true));
        }
    }

    public Component name(Player player) {
        return player.displayName();
    }

    public boolean canSee(CommandSender viewer, Player target) {
        if (!(viewer instanceof Player player) || player.equals(target)) {
            return true;
        }
        PlayerData data = online.get(target.getUniqueId());
        if (data == null || !data.vanished) {
            return true;
        }
        return viewer.hasPermission("anyplugin.vanish.see");
    }

    public int visibleCount() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = online.get(player.getUniqueId());
            if (data == null || !data.vanished) {
                count++;
            }
        }
        return count;
    }

    public Player findOnline(CommandSender viewer, String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null && canSee(viewer, exact)) {
            return exact;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        Player prefixMatch = null;
        int prefixMatches = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canSee(viewer, player)) {
                continue;
            }
            PlayerData data = online.get(player.getUniqueId());
            if (data != null && data.nick != null && Text.stripTags(data.nick).equalsIgnoreCase(name)) {
                return player;
            }
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                prefixMatch = player;
                prefixMatches++;
            }
        }
        return prefixMatches == 1 ? prefixMatch : null;
    }
}
