package org.lts.anyplugin.teleport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.core.Locations;
import org.lts.anyplugin.player.Home;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.p;

public final class HomeCommands {

    private static final String NAME_PATTERN = "[\\p{L}0-9_\\-]{1,24}";

    private final AnyPlugin plugin;
    private final TeleportService teleports;

    public HomeCommands(AnyPlugin plugin, TeleportService teleports) {
        this.plugin = plugin;
        this.teleports = teleports;
    }

    public void register(CommandRegistry registry) {
        registry.cmd("sethome").alias("createhome").perm("anyplugin.sethome").playerOnly().exec(this::setHome);
        registry.cmd("home").alias("h").perm("anyplugin.home").playerOnly().exec(this::home).tab(this::completeHomes);
        registry.cmd("delhome").alias("removehome", "rmhome").perm("anyplugin.delhome").playerOnly().exec(this::delHome).tab(this::completeHomes);
        registry.cmd("homes").perm("anyplugin.home").playerOnly().exec(this::homes)
                .tab(ctx -> ctx.index() == 0 && ctx.has("anyplugin.home.others") ? CommandRegistry.players(ctx.sender) : List.of());
    }

    public int limit(Player player) {
        return plugin.perms().limit(player, "anyplugin.homes.", plugin.getConfig().getInt("homes.default-limit", 3));
    }

    private void setHome(Ctx ctx) {
        Player player = ctx.player();
        PlayerData data = plugin.players().data(player);
        String name = ctx.opt(0, "home");
        if (!name.matches(NAME_PATTERN)) {
            throw new CmdException("home.bad-name");
        }
        String key = name.toLowerCase(Locale.ROOT);
        int limit = limit(player);
        if (!data.homes.containsKey(key) && data.homes.size() >= limit) {
            throw new CmdException("home.limit", p("limit", limit));
        }
        if (plugin.getConfig().getStringList("homes.blocked-worlds").contains(player.getWorld().getName())
                && !player.hasPermission("anyplugin.home.bypass-world")) {
            throw new CmdException("home.world-blocked");
        }
        Location location = player.getLocation();
        Home home = Home.of(name, location);
        data.homes.put(key, home);
        plugin.db().update("INSERT OR REPLACE INTO homes (uuid, name, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?)",
                player.getUniqueId().toString(), key, home.world(), home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        ctx.send("home.set", p("name", name), p("count", data.homes.size()), p("limit", limit == Integer.MAX_VALUE ? "∞" : limit));
    }

    private void home(Ctx ctx) {
        Player player = ctx.player();
        String raw = ctx.opt(0, null);
        if (raw != null && raw.contains(":")) {
            ctx.require("anyplugin.home.others");
            String owner = raw.substring(0, raw.indexOf(':'));
            String name = raw.substring(raw.indexOf(':') + 1).toLowerCase(Locale.ROOT);
            otherHome(player, owner, name);
            return;
        }
        PlayerData data = plugin.players().data(player);
        Home home;
        if (raw == null) {
            home = data.homes.get("home");
            if (home == null && data.homes.size() == 1) {
                home = data.homes.values().iterator().next();
            }
            if (home == null && data.homes.isEmpty()) {
                Location bed = player.getRespawnLocation();
                if (bed != null) {
                    teleports.teleport(player, bed, "home", "home.teleported-bed");
                    return;
                }
                throw new CmdException("home.none");
            }
            if (home == null) {
                homes(ctx);
                return;
            }
        } else {
            home = data.homes.get(raw.toLowerCase(Locale.ROOT));
            if (home == null && raw.equalsIgnoreCase("bed")) {
                Location bed = player.getRespawnLocation();
                if (bed == null) {
                    throw new CmdException("home.no-bed");
                }
                teleports.teleport(player, bed, "home", "home.teleported-bed");
                return;
            }
        }
        if (home == null) {
            throw new CmdException("home.not-found", p("name", raw));
        }
        Location location = home.location();
        if (location == null) {
            throw new CmdException("home.world-missing", p("world", home.world()));
        }
        teleports.teleport(player, location, "home", "home.teleported", p("name", home.name()));
    }

    private void otherHome(Player player, String owner, String name) {
        plugin.players().lookup(owner).thenAccept(found -> {
            if (found.isEmpty()) {
                plugin.msg().send(player, "errors.player-never-joined", p("player", owner));
                return;
            }
            plugin.db().one("SELECT * FROM homes WHERE uuid=? AND name=?", rs -> new Home(rs.getString("name"), rs.getString("world"),
                    rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")),
                    found.get().uuid().toString(), name).thenAccept((Optional<Home> home) -> {
                        Location location = home.map(Home::location).orElse(null);
                        if (location == null) {
                            plugin.msg().send(player, "home.not-found", p("name", owner + ":" + name));
                            return;
                        }
                        plugin.tasks().entity(player, () -> teleports.instant(player, location));
                        plugin.msg().send(player, "home.teleported", p("name", owner + ":" + name));
                    });
        });
    }

    private void delHome(Ctx ctx) {
        Player player = ctx.player();
        PlayerData data = plugin.players().data(player);
        String key = ctx.arg(0).toLowerCase(Locale.ROOT);
        if (data.homes.remove(key) == null) {
            throw new CmdException("home.not-found", p("name", ctx.arg(0)));
        }
        plugin.db().update("DELETE FROM homes WHERE uuid=? AND name=?", player.getUniqueId().toString(), key);
        ctx.send("home.deleted", p("name", ctx.arg(0)));
    }

    private void homes(Ctx ctx) {
        Player player = ctx.player();
        if (ctx.present(0) && !ctx.arg(0).equalsIgnoreCase(player.getName())) {
            ctx.require("anyplugin.home.others");
            String owner = ctx.arg(0);
            plugin.players().lookup(owner).thenAccept(found -> {
                if (found.isEmpty()) {
                    plugin.msg().send(player, "errors.player-never-joined", p("player", owner));
                    return;
                }
                plugin.db().list("SELECT * FROM homes WHERE uuid=?", rs -> new Home(rs.getString("name"), rs.getString("world"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")),
                        found.get().uuid().toString()).thenAccept(list -> show(player, found.get().name(), list, found.get().name() + ":"));
            });
            return;
        }
        PlayerData data = plugin.players().data(player);
        show(player, player.getName(), new ArrayList<>(data.homes.values()), "");
    }

    private void show(Player viewer, String owner, List<Home> homes, String commandPrefix) {
        if (homes.isEmpty()) {
            plugin.msg().send(viewer, "home.list-empty", p("player", owner));
            return;
        }
        homes.sort(Comparator.comparing(Home::name));
        Component line = plugin.msg().get("home.list-header", p("player", owner), p("count", homes.size()));
        Component separator = plugin.msg().get("home.list-separator");
        boolean first = true;
        Component entries = Component.empty();
        for (Home home : homes) {
            if (!first) {
                entries = entries.append(separator);
            }
            first = false;
            Location location = home.location();
            String where = location == null ? home.world() : Locations.brief(location);
            entries = entries.append(plugin.msg().get("home.list-entry", p("name", home.name()))
                    .hoverEvent(HoverEvent.showText(plugin.msg().get("home.list-hover", p("location", where))))
                    .clickEvent(ClickEvent.runCommand("/home " + commandPrefix + home.name())));
        }
        viewer.sendMessage(line);
        viewer.sendMessage(entries);
    }

    private List<String> completeHomes(Ctx ctx) {
        if (ctx.index() != 0 || !(ctx.sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Home home : plugin.players().data(player).homes.values()) {
            names.add(home.name());
        }
        return names;
    }
}
