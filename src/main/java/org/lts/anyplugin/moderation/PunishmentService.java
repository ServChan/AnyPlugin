package org.lts.anyplugin.moderation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.chat.ChatService;
import org.lts.anyplugin.core.Durations;
import org.lts.anyplugin.core.Text;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.p;

public final class PunishmentService {

    public record Target(UUID uuid, String name, String ip) {
    }

    private final AnyPlugin plugin;

    public PunishmentService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public Component loginBlock(UUID uuid, String ip) {
        try {
            long now = System.currentTimeMillis();
            Optional<Punishment> ban = plugin.db().one(
                    "SELECT * FROM punishments WHERE active=1 AND (expires IS NULL OR expires>?) AND ((type='ban' AND uuid=?) OR (type='ipban' AND ip=? AND ip IS NOT NULL)) ORDER BY created DESC LIMIT 1",
                    Punishment::read, now, uuid.toString(), ip).get(10, TimeUnit.SECONDS);
            return ban.map(this::screen).orElse(null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not check bans", e);
            return null;
        }
    }

    public Component screen(Punishment punishment) {
        String key = switch (punishment.type()) {
            case IPBAN -> "punish.screen-ipban";
            case KICK -> "punish.screen-kick";
            default -> "punish.screen-ban";
        };
        return plugin.msg().get(key, resolvers(punishment));
    }

    public TagResolver resolvers(Punishment punishment) {
        String remaining = punishment.expires() == null ? plugin.msg().raw("time.forever")
                : plugin.msg().duration(punishment.expires() - System.currentTimeMillis());
        String until = punishment.expires() == null ? plugin.msg().raw("time.forever") : Text.date(punishment.expires());
        return TagResolver.resolver(
                p("id", punishment.id()),
                p("player", punishment.name() == null ? "?" : punishment.name()),
                p("reason", punishment.reason() == null || punishment.reason().isEmpty() ? plugin.msg().raw("punish.no-reason") : punishment.reason()),
                p("actor", punishment.actor()),
                p("remaining", remaining),
                p("until", until),
                p("date", Text.date(punishment.created())),
                p("type", plugin.msg().raw("punish.type." + punishment.type().key())));
    }

    public CompletableFuture<Optional<Target>> resolve(CommandSender viewer, String name) {
        Player online = plugin.players().findOnline(viewer, name);
        if (online != null) {
            PlayerData data = plugin.players().data(online);
            return CompletableFuture.completedFuture(Optional.of(new Target(online.getUniqueId(), online.getName(), data.lastIp)));
        }
        return plugin.players().lookup(name).thenApply(found -> found.map(r -> new Target(r.uuid(), r.name(), r.lastIp())));
    }

    public String[] template(String reason) {
        if (reason == null || !reason.startsWith("#")) {
            return null;
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("moderation.templates." + reason.substring(1).toLowerCase(Locale.ROOT));
        if (section == null) {
            return null;
        }
        return new String[] {section.getString("reason", reason), section.getString("duration", "")};
    }

    public CompletableFuture<Punishment> punish(Target target, Punishment.Type type, String reason, Long durationMillis, CommandSender actor) {
        long now = System.currentTimeMillis();
        Long expires = durationMillis == null ? null : now + durationMillis;
        boolean active = type != Punishment.Type.KICK;
        String actorName = actor instanceof Player ? actor.getName() : plugin.msg().raw("punish.console-name");
        return plugin.db().insert("INSERT INTO punishments (uuid, name, ip, type, reason, actor, created, expires, active) VALUES (?,?,?,?,?,?,?,?,?)",
                        target.uuid() == null ? null : target.uuid().toString(), target.name(), target.ip(), type.key(), reason, actorName, now, expires, active)
                .thenApply(id -> {
                    Punishment punishment = new Punishment(id, target.uuid(), target.name(), target.ip(), type, reason, actorName, now, expires,
                            active, null, null);
                    apply(punishment);
                    announce(punishment, actor);
                    return punishment;
                });
    }

    private void apply(Punishment punishment) {
        switch (punishment.type()) {
            case BAN, KICK -> {
                Player player = punishment.uuid() == null ? null : Bukkit.getPlayer(punishment.uuid());
                if (player != null) {
                    Component screen = screen(punishment);
                    plugin.tasks().entity(player, () -> player.kick(screen));
                }
            }
            case IPBAN -> {
                Component screen = screen(punishment);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = plugin.players().data(player.getUniqueId());
                    boolean sameIp = data != null && punishment.ip() != null && punishment.ip().equals(data.lastIp);
                    if (sameIp || player.getUniqueId().equals(punishment.uuid())) {
                        plugin.tasks().entity(player, () -> player.kick(screen));
                    }
                }
            }
            case MUTE -> {
                PlayerData data = punishment.uuid() == null ? null : plugin.players().data(punishment.uuid());
                if (data != null) {
                    data.mute = punishment;
                    Player player = Bukkit.getPlayer(punishment.uuid());
                    if (player != null) {
                        plugin.msg().send(player, "punish.muted-notify", resolvers(punishment));
                    }
                }
            }
            case WARN -> {
                Player player = punishment.uuid() == null ? null : Bukkit.getPlayer(punishment.uuid());
                if (player != null) {
                    plugin.msg().send(player, "punish.warned-notify", resolvers(punishment));
                }
                escalate(punishment);
            }
        }
    }

    private void escalate(Punishment warn) {
        ConfigurationSection actions = plugin.getConfig().getConfigurationSection("moderation.warn-actions");
        if (actions == null || warn.uuid() == null) {
            return;
        }
        plugin.db().one("SELECT COUNT(*) AS c FROM punishments WHERE uuid=? AND type='warn' AND active=1", rs -> rs.getInt("c"), warn.uuid().toString())
                .thenAccept(count -> {
                    Map<Integer, String> sorted = new TreeMap<>();
                    for (String key : actions.getKeys(false)) {
                        try {
                            sorted.put(Integer.parseInt(key), actions.getString(key));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    String action = sorted.get(count.orElse(0));
                    if (action == null || action.isBlank()) {
                        return;
                    }
                    String[] parts = action.trim().split("\\s+", 2);
                    Punishment.Type type = switch (parts[0].toLowerCase(Locale.ROOT)) {
                        case "ban", "tempban" -> Punishment.Type.BAN;
                        case "mute", "tempmute" -> Punishment.Type.MUTE;
                        case "kick" -> Punishment.Type.KICK;
                        default -> null;
                    };
                    if (type == null) {
                        plugin.getLogger().warning("Unknown warn action: " + action);
                        return;
                    }
                    long duration = parts.length > 1 ? Durations.parse(parts[1]) : -1;
                    String reason = plugin.msg().raw("punish.auto-reason").replace("<count>", String.valueOf(count.orElse(0)));
                    punish(new Target(warn.uuid(), warn.name(), warn.ip()), type, reason, duration > 0 ? duration : null, Bukkit.getConsoleSender());
                });
    }

    private void announce(Punishment punishment, CommandSender actor) {
        Component line = plugin.msg().get("punish.broadcast." + punishment.type().key(), resolvers(punishment));
        if (plugin.getConfig().getBoolean("moderation.broadcast-to-all", false)) {
            Bukkit.getServer().sendMessage(line);
        } else {
            ChatService.broadcastPermission("anyplugin.notify.punish", line);
            if (actor instanceof Player player && !player.hasPermission("anyplugin.notify.punish")) {
                player.sendMessage(line);
            }
        }
        if (plugin.staff() != null) {
            plugin.staff().log(actor, punishment.type().key() + " " + punishment.name() + " #" + punishment.id() + " " + (punishment.reason() == null ? "" : punishment.reason()));
        }
    }

    public CompletableFuture<Integer> revoke(UUID uuid, String ip, List<Punishment.Type> types, CommandSender actor) {
        long now = System.currentTimeMillis();
        StringBuilder in = new StringBuilder();
        for (Punishment.Type type : types) {
            if (!in.isEmpty()) {
                in.append(',');
            }
            in.append('\'').append(type.key()).append('\'');
        }
        String actorName = actor instanceof Player ? actor.getName() : plugin.msg().raw("punish.console-name");
        String sql = "UPDATE punishments SET active=0, removed_by=?, removed_at=? WHERE active=1 AND type IN (" + in + ") AND (uuid=?"
                + (ip != null && types.contains(Punishment.Type.IPBAN) ? " OR (type='ipban' AND ip=?)" : "") + ")";
        CompletableFuture<Integer> result = ip != null && types.contains(Punishment.Type.IPBAN)
                ? plugin.db().update(sql, actorName, now, uuid == null ? "" : uuid.toString(), ip)
                : plugin.db().update(sql, actorName, now, uuid == null ? "" : uuid.toString());
        if (types.contains(Punishment.Type.MUTE) && uuid != null) {
            PlayerData data = plugin.players().data(uuid);
            if (data != null) {
                data.mute = null;
            }
        }
        if (plugin.staff() != null) {
            plugin.staff().log(actor, "revoke " + types + " " + uuid);
        }
        return result;
    }

    public CompletableFuture<Integer> revokeById(long id, CommandSender actor) {
        String actorName = actor instanceof Player ? actor.getName() : plugin.msg().raw("punish.console-name");
        return plugin.db().update("UPDATE punishments SET active=0, removed_by=?, removed_at=? WHERE id=? AND active=1", actorName, System.currentTimeMillis(), id);
    }

    public CompletableFuture<List<Punishment>> history(UUID uuid, int limit) {
        return plugin.db().list("SELECT * FROM punishments WHERE uuid=? ORDER BY created DESC LIMIT ?", Punishment::read, uuid.toString(), limit);
    }

    public CompletableFuture<List<Punishment>> activeWarns(UUID uuid) {
        return plugin.db().list("SELECT * FROM punishments WHERE uuid=? AND type='warn' AND active=1 ORDER BY created DESC", Punishment::read, uuid.toString());
    }
}
