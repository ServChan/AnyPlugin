package org.lts.anyplugin.tab;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.hooks.Papi;
import org.lts.anyplugin.perms.Group;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.c;
import static org.lts.anyplugin.core.Messages.mm;
import static org.lts.anyplugin.core.Messages.p;

public final class TabService implements Listener {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final AnyPlugin plugin;
    private NametagService nametags;

    public TabService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void start() {
        if (cfg().getBoolean("nametags.enabled", true)) {
            try {
                nametags = new NametagService(plugin);
            } catch (LinkageError e) {
                plugin.getLogger().log(Level.WARNING, "Name tags are not available on this server build", e);
            }
        }
        long period = Math.max(10, cfg().getLong("tab.update-ticks", 40));
        plugin.tasks().globalTimer(period, period, () -> plugin.tasks().forEachOnline(this::sendHeader));
    }

    public void stop() {
        if (nametags != null) {
            nametags.clear();
        }
    }

    private TagResolver placeholders(Player player) {
        PlayerData data = plugin.players().data(player);
        return TagResolver.resolver(
                p("online", plugin.players().visibleCount()),
                p("max", Bukkit.getMaxPlayers()),
                p("unique", plugin.players().uniquePlayers()),
                p("ping", player.getPing()),
                p("tps", ServerStats.format(ServerStats.tps())),
                p("mspt", ServerStats.format(ServerStats.mspt())),
                p("world", player.getWorld().getName()),
                p("player", player.getName()),
                c("name", player.displayName()),
                p("group", plugin.perms().primary(player).name),
                p("time", LocalTime.now().format(CLOCK)),
                p("uptime", plugin.msg().duration(ServerStats.uptimeMillis())),
                p("playtime", plugin.msg().duration(data.totalPlaytime())));
    }

    private Component lines(Player player, List<String> lines, TagResolver resolvers) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out = out.append(Component.newline());
            }
            out = out.append(plugin.msg().parse(Papi.apply(player, lines.get(i)), resolvers));
        }
        return out;
    }

    public void sendHeader(Player player) {
        if (!cfg().getBoolean("tab.header-footer", true)) {
            return;
        }
        TagResolver resolvers = placeholders(player);
        Component header = lines(player, plugin.msg().rawList("tab.header"), resolvers);
        Component footer = lines(player, plugin.msg().rawList("tab.footer"), resolvers);
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    public void refresh(Player player) {
        if (!player.isOnline()) {
            return;
        }
        PlayerData data = plugin.players().data(player);
        Group group = plugin.perms().primary(player);
        String prefix = plugin.perms().prefix(player);
        String suffix = plugin.perms().suffix(player);
        Component name = player.displayName();
        NamedTextColor color = group.nameColor.isEmpty() ? null : NamedTextColor.NAMES.value(group.nameColor.toLowerCase(java.util.Locale.ROOT));
        if (color != null) {
            name = name.colorIfAbsent(color);
        }
        Component afk = data.afk ? plugin.msg().get("tab.afk-tag") : Component.empty();
        Component vanish = data.vanished ? plugin.msg().get("tab.vanish-tag") : Component.empty();
        TagResolver resolvers = TagResolver.resolver(mm("prefix", prefix), mm("suffix", suffix), c("name", name), c("afk", afk),
                c("vanish", vanish), p("player", player.getName()), p("group", group.name));
        if (cfg().getBoolean("tab.player-names", true)) {
            player.playerListName(plugin.msg().parse(Papi.apply(player, cfg().getString("tab.name-format", "<prefix><name><suffix><afk><vanish>")), resolvers));
        }
        if (cfg().getBoolean("tab.sort-by-weight", true)) {
            player.setPlayerListOrder(Math.max(0, group.weight));
        }
        if (nametags != null) {
            Component tagPrefix = plugin.msg().parse(cfg().getString("nametags.prefix", "<prefix>"), resolvers);
            Component tagSuffix = plugin.msg().parse(cfg().getString("nametags.suffix", "<suffix><afk>"), resolvers);
            nametags.update(player, tagPrefix, tagSuffix, group.nameColor, group.weight);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.tasks().entityLater(player, 2, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (nametags != null) {
                nametags.sendAllTo(player);
            }
            refresh(player);
            sendHeader(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (nametags != null) {
            nametags.remove(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(PaperServerListPingEvent event) {
        boolean maintenance = plugin.maintenance() != null && plugin.maintenance().enabled();
        String key = maintenance ? "server-list.maintenance" : "server-list.motd";
        List<String> motd = plugin.msg().rawList(key);
        if (!motd.isEmpty()) {
            Component out = Component.empty();
            for (int i = 0; i < Math.min(2, motd.size()); i++) {
                if (i > 0) {
                    out = out.append(Component.newline());
                }
                out = out.append(plugin.msg().parse(motd.get(i), p("online", plugin.players().visibleCount()), p("max", Bukkit.getMaxPlayers())));
            }
            event.motd(out);
        }
        int maxShown = cfg().getInt("server-list.max-players", -1);
        if (maxShown > 0) {
            event.setMaxPlayers(maxShown);
        }
        event.setNumPlayers(plugin.players().visibleCount());
        event.getListedPlayers().removeIf(info -> {
            PlayerData data = plugin.players().data(info.id());
            return data != null && data.vanished;
        });
        if (cfg().getBoolean("server-list.hide-players", false)) {
            event.setHidePlayers(true);
        }
    }
}
