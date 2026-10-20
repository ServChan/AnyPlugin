package org.lts.anyplugin.server;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;

import static org.lts.anyplugin.core.Messages.p;

public final class RestartService implements Listener {

    private final AnyPlugin plugin;
    private volatile long target;
    private volatile long scheduledFor;
    private volatile String reason = "";
    private volatile long lastAnnounced = Long.MAX_VALUE;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private volatile boolean barShown;
    private volatile boolean stopping;

    public RestartService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        scheduleDaily();
        plugin.tasks().globalTimer(20, 20, this::tick);
    }

    private void scheduleDaily() {
        List<String> times = plugin.getConfig().getStringList("restart.daily");
        long now = System.currentTimeMillis();
        long best = 0;
        LocalDateTime current = LocalDateTime.now();
        for (String raw : times) {
            try {
                LocalTime time = LocalTime.parse(raw.trim());
                LocalDateTime next = current.toLocalDate().atTime(time);
                if (!next.isAfter(current.plusMinutes(1))) {
                    next = next.plusDays(1);
                }
                long millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (best == 0 || millis < best) {
                    best = millis;
                }
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Bad restart time: " + raw);
            }
        }
        if (best > now) {
            schedule(best, plugin.msg().raw("restart.daily-reason"));
        }
    }

    private void schedule(long when, String why) {
        target = when;
        scheduledFor = when - System.currentTimeMillis();
        reason = why == null ? "" : why;
        lastAnnounced = Long.MAX_VALUE;
    }

    public long remaining() {
        return target == 0 ? -1 : Math.max(0, target - System.currentTimeMillis());
    }

    public void register(CommandRegistry registry) {
        registry.cmd("restart").alias("reboot").perm("anyplugin.restart").logged().exec(this::command)
                .tab(ctx -> ctx.index() == 0 ? List.of("5m", "10m", "1m", "now", "cancel", "status") : List.of());
    }

    private void command(Ctx ctx) {
        String arg = ctx.opt(0, "status").toLowerCase(Locale.ROOT);
        switch (arg) {
            case "status" -> {
                if (target == 0) {
                    ctx.send("restart.none");
                } else {
                    ctx.send("restart.status", p("time", plugin.msg().duration(target - System.currentTimeMillis())));
                }
            }
            case "cancel" -> {
                if (target == 0) {
                    throw new CmdException("restart.none");
                }
                target = 0;
                hideBar();
                Bukkit.getServer().sendMessage(plugin.msg().get("restart.cancelled"));
            }
            case "now" -> {
                schedule(System.currentTimeMillis() + 5_000, ctx.joinedOpt(1));
                ctx.send("restart.scheduled", p("time", plugin.msg().duration(5_000)));
            }
            default -> {
                long delay = ctx.duration(0);
                schedule(System.currentTimeMillis() + delay, ctx.joinedOpt(1));
                ctx.send("restart.scheduled", p("time", plugin.msg().duration(delay)));
            }
        }
    }

    private void tick() {
        if (target == 0 || stopping) {
            return;
        }
        long left = target - System.currentTimeMillis();
        long seconds = Math.max(0, (left + 999) / 1000);
        for (int mark : plugin.getConfig().getIntegerList("restart.warnings")) {
            if (seconds <= mark && lastAnnounced > mark) {
                lastAnnounced = mark;
                Bukkit.getServer().sendMessage(plugin.msg().get("restart.warning", p("time", plugin.msg().duration(mark * 1000L)),
                        p("reason", reason.isEmpty() ? plugin.msg().raw("restart.no-reason") : reason)));
                break;
            }
        }
        long barFrom = plugin.getConfig().getLong("restart.bossbar-seconds", 300);
        if (seconds <= barFrom) {
            bar.name(plugin.msg().get("restart.bossbar", p("time", plugin.msg().duration(seconds * 1000))));
            float total = Math.max(1, Math.min(barFrom, scheduledFor / 1000));
            bar.progress(Math.max(0f, Math.min(1f, seconds / total)));
            if (!barShown) {
                barShown = true;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showBossBar(bar);
                }
            }
        }
        if (left <= 0) {
            stopping = true;
            Component kick = plugin.msg().get("restart.kick", p("reason", reason));
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.tasks().entity(player, () -> player.kick(kick));
            }
            plugin.tasks().globalLater(40, Bukkit::shutdown);
        }
    }

    private void hideBar() {
        if (barShown) {
            barShown = false;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (barShown) {
            event.getPlayer().showBossBar(bar);
        }
    }
}
