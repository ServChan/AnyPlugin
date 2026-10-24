package org.lts.anyplugin.moderation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.chat.ChatService;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.core.Locations;
import org.lts.anyplugin.core.Text;
import org.lts.anyplugin.player.PlayerData;
import org.lts.anyplugin.util.ViewGuard;

import static org.lts.anyplugin.core.Messages.c;
import static org.lts.anyplugin.core.Messages.p;

public final class StaffService implements Listener {

    private final AnyPlugin plugin;
    private final ViewGuard views;
    private final Map<UUID, Long> reportCooldown = new ConcurrentHashMap<>();
    private final Set<UUID> frozenOffline = ConcurrentHashMap.newKeySet();

    public StaffService(AnyPlugin plugin, ViewGuard views) {
        this.plugin = plugin;
        this.views = views;
    }

    public void log(CommandSender actor, String action) {
        plugin.db().update("INSERT INTO staff_log (time, actor, action) VALUES (?,?,?)", System.currentTimeMillis(), actor.getName(), action);
    }

    public void register(CommandRegistry r) {
        r.cmd("invsee").alias("inv").perm("anyplugin.invsee").playerOnly().logged().exec(ctx -> {
            Player viewer = ctx.player();
            Player target = ctx.online(0);
            if (target.equals(viewer)) {
                throw new CmdException("invsee.self");
            }
            views.openOther(viewer, target, target.getInventory(), plugin.msg().get("invsee.title", p("player", target.getName())),
                    ctx.has("anyplugin.invsee.edit"));
        }).tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
        r.cmd("freeze").alias("ss").perm("anyplugin.freeze").logged().exec(ctx -> {
            Player target = ctx.online(0);
            if (ctx.sender instanceof Player actor && actor.equals(target)) {
                throw new CmdException("freeze.self");
            }
            if (target.hasPermission(PunishmentService.EXEMPT) && ctx.sender instanceof Player) {
                throw new CmdException("punish.exempt", p("player", target.getName()));
            }
            PlayerData data = plugin.players().data(target);
            data.frozen = !data.frozen;
            plugin.msg().send(target, data.frozen ? "freeze.frozen" : "freeze.unfrozen");
            ChatService.broadcastPermission("anyplugin.freeze", plugin.msg().get(data.frozen ? "freeze.notify-on" : "freeze.notify-off",
                    p("player", target.getName()), p("actor", ctx.sender.getName())));
        }).tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
        r.cmd("commandspy").alias("cspy").perm("anyplugin.commandspy").playerOnly().exec(ctx -> {
            PlayerData data = plugin.players().data(ctx.player());
            data.commandSpy = !data.commandSpy;
            plugin.players().setFlag(data, "commandspy", data.commandSpy);
            ctx.send(data.commandSpy ? "spy.command-on" : "spy.command-off");
        });
        r.cmd("staffchat").alias("sc", "a").perm("anyplugin.staffchat").exec(ctx -> {
            if (!ctx.present(0)) {
                PlayerData data = plugin.players().data(ctx.player());
                data.staffChat = !data.staffChat;
                ctx.send(data.staffChat ? "staffchat.toggle-on" : "staffchat.toggle-off");
                return;
            }
            staffChat(ctx.sender, ctx.joined(0));
        });
        r.cmd("report").perm("anyplugin.report").playerOnly().exec(this::report)
                .tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
        r.cmd("reports").perm("anyplugin.reports").exec(this::reports).tab(ctx -> ctx.index() == 0 ? List.of("all", "close", "tp") : List.of());
    }

    public void staffChat(CommandSender sender, String text) {
        Component name = sender instanceof Player player ? player.displayName() : Component.text(sender.getName());
        Component line = plugin.msg().get("staffchat.format", c("player", name), c("message", Text.userInput(text, true)));
        ChatService.broadcastPermission("anyplugin.staffchat", line);
    }

    private void report(Ctx ctx) {
        Player reporter = ctx.player();
        Player target = ctx.online(0);
        String reason = ctx.joined(1);
        if (target.equals(reporter)) {
            throw new CmdException("report.self");
        }
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("moderation.report-cooldown-seconds", 60) * 1000;
        Long last = reportCooldown.get(reporter.getUniqueId());
        if (last != null && now - last < cooldown && !reporter.hasPermission("anyplugin.reports")) {
            throw new CmdException("report.cooldown", p("time", plugin.msg().duration(cooldown - (now - last))));
        }
        reportCooldown.put(reporter.getUniqueId(), now);
        String where = Locations.serialize(target.getLocation());
        plugin.db().insert("INSERT INTO reports (reporter, reporter_name, target, target_name, reason, location, created) VALUES (?,?,?,?,?,?,?)",
                reporter.getUniqueId().toString(), reporter.getName(), target.getUniqueId().toString(), target.getName(), reason, where, now)
                .thenAccept(id -> {
                    ctx.send("report.sent", p("player", target.getName()));
                    Component line = plugin.msg().get("report.notify", p("id", id), p("reporter", reporter.getName()), p("player", target.getName()),
                                    p("reason", reason))
                            .clickEvent(ClickEvent.runCommand("/reports tp " + id));
                    ChatService.broadcastPermission("anyplugin.reports", line);
                });
    }

    private record Report(long id, String reporter, String target, String reason, String location, long created, String closedBy) {
    }

    private void reports(Ctx ctx) {
        String action = ctx.opt(0, "open").toLowerCase(Locale.ROOT);
        switch (action) {
            case "close" -> {
                long id = ctx.integer(1, 1, Integer.MAX_VALUE);
                plugin.db().update("UPDATE reports SET closed_by=?, closed_at=? WHERE id=? AND closed_by IS NULL", ctx.sender.getName(), System.currentTimeMillis(), id)
                        .thenAccept(count -> ctx.send(count > 0 ? "report.closed" : "report.missing", p("id", id)));
            }
            case "tp" -> {
                long id = ctx.integer(1, 1, Integer.MAX_VALUE);
                Player player = ctx.player();
                plugin.db().one("SELECT location FROM reports WHERE id=?", rs -> rs.getString("location"), id).thenAccept(found -> {
                    Location location = found.map(Locations::deserialize).orElse(null);
                    if (location == null) {
                        ctx.send("report.missing", p("id", id));
                        return;
                    }
                    if (plugin.teleports() != null) {
                        plugin.teleports().instant(player, location);
                    } else {
                        plugin.tasks().entity(player, () -> player.teleportAsync(location));
                    }
                });
            }
            case "open", "all" -> {
                String where = action.equals("all") ? "" : " WHERE closed_by IS NULL";
                plugin.db().list("SELECT * FROM reports" + where + " ORDER BY id DESC LIMIT 15", rs -> new Report(rs.getLong("id"),
                        rs.getString("reporter_name"), rs.getString("target_name"), rs.getString("reason"), rs.getString("location"),
                        rs.getLong("created"), rs.getString("closed_by"))).thenAccept(list -> {
                            if (list.isEmpty()) {
                                ctx.send("report.none");
                                return;
                            }
                            ctx.send("report.list-header", p("count", list.size()));
                            for (Report report : list) {
                                Component line = plugin.msg().get(report.closedBy() == null ? "report.list-line" : "report.list-line-closed",
                                        p("id", report.id()), p("reporter", report.reporter()), p("player", report.target()),
                                        p("reason", report.reason()), p("date", Text.date(report.created())),
                                        p("closed_by", report.closedBy() == null ? "" : report.closedBy()));
                                ctx.sender.sendMessage(line.clickEvent(ClickEvent.suggestCommand("/reports close " + report.id())));
                            }
                        });
            }
            default -> throw ctx.usage();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("anyplugin.commandspy.exempt")) {
            return;
        }
        String message = event.getMessage();
        String label = message.length() > 1 ? message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT) : "";
        if (plugin.getConfig().getStringList("moderation.commandspy-ignore").contains(label)) {
            return;
        }
        Component line = null;
        for (PlayerData data : plugin.players().all()) {
            if (!data.commandSpy || data.uuid.equals(player.getUniqueId())) {
                continue;
            }
            Player spy = Bukkit.getPlayer(data.uuid);
            if (spy != null && spy.hasPermission("anyplugin.commandspy")) {
                if (line == null) {
                    line = plugin.msg().get("spy.command", p("player", player.getName()), p("command", message));
                }
                spy.sendMessage(line);
            }
        }
    }

    private boolean frozen(Player player) {
        PlayerData data = plugin.players().data(player.getUniqueId());
        return data != null && data.frozen;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent event) {
        if (!frozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenCommand(PlayerCommandPreprocessEvent event) {
        if (!frozen(event.getPlayer())) {
            return;
        }
        String label = event.getMessage().length() > 1 ? event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT) : "";
        if (!plugin.getConfig().getStringList("moderation.freeze-allowed-commands").contains(label)) {
            event.setCancelled(true);
            plugin.msg().send(event.getPlayer(), "freeze.blocked");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenInteract(PlayerInteractEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenBreak(BlockBreakEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenPlace(BlockPlaceEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenDrop(PlayerDropItemEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrozenDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && frozen(player)) {
            event.setCancelled(true);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player damager && frozen(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (frozenOffline.remove(player.getUniqueId())) {
            plugin.players().data(player).frozen = true;
            plugin.msg().send(player, "freeze.frozen");
            ChatService.broadcastPermission("anyplugin.freeze", plugin.msg().get("freeze.rejoin", p("player", player.getName())));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (frozen(event.getPlayer())) {
            frozenOffline.add(event.getPlayer().getUniqueId());
            ChatService.broadcastPermission("anyplugin.freeze", plugin.msg().get("freeze.quit", p("player", event.getPlayer().getName())));
        }
    }
}
