package org.lts.anyplugin.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.Cmd;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.core.Tasks;
import org.lts.anyplugin.tab.ServerStats;

import static org.lts.anyplugin.core.Messages.p;

public final class InfoCommands {

    private final AnyPlugin plugin;
    private final AtomicInteger announcement = new AtomicInteger();

    public InfoCommands(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = plugin.getConfig().getLong("announcer.interval-seconds", 600);
        if (!plugin.getConfig().getBoolean("announcer.enabled", true) || interval <= 0) {
            return;
        }
        plugin.tasks().globalTimer(interval * 20, interval * 20, this::announce);
    }

    private void announce() {
        List<String> lines = plugin.msg().rawList("announcements");
        if (lines.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        int index = plugin.getConfig().getBoolean("announcer.random", false)
                ? ThreadLocalRandom.current().nextInt(lines.size())
                : Math.floorMod(announcement.getAndIncrement(), lines.size());
        Bukkit.getServer().sendMessage(plugin.msg().parse(plugin.msg().raw("announcer-format").replace("<message>", lines.get(index))));
    }

    public void register(CommandRegistry r) {
        r.cmd("rules").perm("anyplugin.rules").exec(ctx -> plugin.msg().sendList(ctx.sender, "rules"));
        r.cmd("motd").perm("anyplugin.motd").exec(ctx -> plugin.msg().sendList(ctx.sender, "motd", p("player", ctx.sender.getName()),
                p("online", plugin.players().visibleCount())));
        r.cmd("serverinfo").alias("tps", "lag", "gc").perm("anyplugin.serverinfo").exec(this::serverInfo);
        r.cmd("help").alias("?").perm("anyplugin.help").exec(this::help).tab(ctx -> List.of("1", "2", "3"));
        ConfigurationSection links = plugin.getConfig().getConfigurationSection("links");
        if (links != null) {
            for (String key : links.getKeys(false)) {
                String label = key.toLowerCase(Locale.ROOT);
                if (!label.matches("[a-z0-9_]{1,24}")) {
                    continue;
                }
                r.cmd(label).exec(ctx -> ctx.sender.sendMessage(plugin.msg().parse(plugin.getConfig().getString("links." + key, ""))));
            }
        }
    }

    private void serverInfo(Ctx ctx) {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576;
        long max = runtime.maxMemory() / 1_048_576;
        ctx.send("serverinfo.header", p("software", Bukkit.getName() + " " + Bukkit.getMinecraftVersion()), p("folia", Tasks.FOLIA ? "Folia" : "Paper"));
        ctx.send("serverinfo.tps", p("tps", ServerStats.format(ServerStats.tps())), p("mspt", ServerStats.format(ServerStats.mspt())),
                p("scope", plugin.msg().raw(Tasks.FOLIA ? "serverinfo.scope-region" : "serverinfo.scope-server")));
        ctx.send("serverinfo.memory", p("used", used), p("max", max));
        ctx.send("serverinfo.uptime", p("time", plugin.msg().duration(ServerStats.uptimeMillis())));
        ctx.send("serverinfo.players", p("online", plugin.players().visibleCount()), p("max", Bukkit.getMaxPlayers()),
                p("unique", plugin.players().uniquePlayers()));
        if (!Tasks.FOLIA) {
            for (World world : Bukkit.getWorlds()) {
                ctx.send("serverinfo.world", p("world", world.getName()), p("chunks", world.getLoadedChunks().length),
                        p("entities", world.getEntityCount()), p("players", world.getPlayerCount()));
            }
        }
    }

    private void help(Ctx ctx) {
        List<Cmd> visible = new ArrayList<>();
        for (Cmd cmd : plugin.commands().all()) {
            if (cmd.canUse(ctx.sender) && plugin.msg().has("desc." + cmd.name())) {
                visible.add(cmd);
            }
        }
        visible.sort((a, b) -> a.name().compareTo(b.name()));
        int perPage = 10;
        int pages = Math.max(1, (visible.size() + perPage - 1) / perPage);
        int page = ctx.present(0) ? ctx.integer(0, 1, pages) : 1;
        ctx.send("help.header", p("page", page), p("pages", pages));
        for (int i = (page - 1) * perPage; i < Math.min(visible.size(), page * perPage); i++) {
            Cmd cmd = visible.get(i);
            Component line = plugin.msg().get("help.line", p("command", "/" + cmd.name()), plugin.msg().has("desc." + cmd.name())
                    ? org.lts.anyplugin.core.Messages.mm("description", plugin.msg().raw("desc." + cmd.name()))
                    : p("description", ""));
            ctx.sender.sendMessage(line.clickEvent(ClickEvent.suggestCommand("/" + cmd.name() + " ")));
        }
        if (page < pages) {
            ctx.sender.sendMessage(plugin.msg().get("help.next", p("next", page + 1)).clickEvent(ClickEvent.runCommand("/help " + (page + 1))));
        }
    }
}
