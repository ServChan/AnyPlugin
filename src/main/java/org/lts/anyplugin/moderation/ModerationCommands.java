package org.lts.anyplugin.moderation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.core.Durations;

import static org.lts.anyplugin.core.Messages.p;

public final class ModerationCommands {

    private static final Pattern IP = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+:[0-9a-fA-F:]+$");

    private final AnyPlugin plugin;
    private final PunishmentService service;

    public ModerationCommands(AnyPlugin plugin, PunishmentService service) {
        this.plugin = plugin;
        this.service = service;
    }

    private static List<String> durations() {
        return List.of("30m", "1h", "12h", "1d", "3d", "7d", "30d");
    }

    private List<String> templates() {
        var section = plugin.getConfig().getConfigurationSection("moderation.templates");
        return section == null ? List.of() : section.getKeys(false).stream().map(key -> "#" + key).toList();
    }

    public void register(CommandRegistry r) {
        r.cmd("ban").perm("anyplugin.ban").exec(ctx -> punish(ctx, Punishment.Type.BAN, false, true)).tab(this::punishTab);
        r.cmd("tempban").perm("anyplugin.ban").exec(ctx -> punish(ctx, Punishment.Type.BAN, true, true)).tab(this::punishTab);
        r.cmd("ipban").alias("banip").perm("anyplugin.ipban").exec(ctx -> punish(ctx, Punishment.Type.IPBAN, false, true)).tab(this::punishTab);
        r.cmd("unban").alias("pardon", "unbanip", "pardon-ip").perm("anyplugin.unban").exec(this::unban);
        r.cmd("mute").perm("anyplugin.mute").exec(ctx -> punish(ctx, Punishment.Type.MUTE, false, true)).tab(this::punishTab);
        r.cmd("tempmute").perm("anyplugin.mute").exec(ctx -> punish(ctx, Punishment.Type.MUTE, true, true)).tab(this::punishTab);
        r.cmd("unmute").perm("anyplugin.unmute").exec(this::unmute).tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
        r.cmd("warn").perm("anyplugin.warn").exec(ctx -> punish(ctx, Punishment.Type.WARN, false, false)).tab(this::punishTab);
        r.cmd("unwarn").alias("delwarn").perm("anyplugin.unwarn").exec(ctx -> {
            long id = ctx.integer(0, 1, Integer.MAX_VALUE);
            service.revokeWarn(id, ctx.sender).thenAccept(count -> ctx.send(count > 0 ? "punish.unwarned" : "punish.unwarn-missing", p("id", id)));
        });
        r.cmd("warnings").alias("warns").perm("anyplugin.warnings").exec(this::warnings)
                .tab(ctx -> ctx.index() == 0 && ctx.has("anyplugin.warnings.others") ? CommandRegistry.players(ctx.sender) : List.of());
        r.cmd("kick").perm("anyplugin.kick").exec(ctx -> {
            Player target = ctx.online(0);
            checkExempt(ctx, target);
            String reason = reason(ctx.joinedOpt(1))[0];
            String ip = plugin.players().data(target).lastIp;
            service.punish(new PunishmentService.Target(target.getUniqueId(), target.getName(), ip), Punishment.Type.KICK, reason, null, ctx.sender)
                    .thenAccept(done -> ctx.send("punish.done.kick", service.resolvers(done)));
        }).tab(this::punishTab);
        r.cmd("history").alias("punishments").perm("anyplugin.history").exec(this::history)
                .tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
    }

    private List<String> punishTab(Ctx ctx) {
        if (ctx.index() == 0) {
            return CommandRegistry.players(ctx.sender);
        }
        if (ctx.index() == 1) {
            List<String> out = new java.util.ArrayList<>(durations());
            out.addAll(templates());
            return out;
        }
        return templates();
    }

    private void checkExempt(Ctx ctx, Player target) {
        if (ctx.sender instanceof Player actor) {
            if (actor.equals(target)) {
                throw new CmdException("punish.self");
            }
            if (target.hasPermission(PunishmentService.EXEMPT)) {
                throw new CmdException("punish.exempt", p("player", target.getName()));
            }
        }
    }

    private String[] reason(String raw) {
        String[] template = service.template(raw);
        if (template == null) {
            return new String[] {raw.isEmpty() ? null : raw, null};
        }
        return new String[] {template[0], template[1]};
    }

    private void punish(Ctx ctx, Punishment.Type type, boolean requireDuration, boolean allowDuration) {
        String name = ctx.arg(0);
        int reasonIndex = 1;
        Long duration = null;
        if (allowDuration && ctx.present(1) && Durations.looksLikeDuration(ctx.arg(1))) {
            duration = ctx.duration(1);
            reasonIndex = 2;
        }
        String[] reason = reason(ctx.joinedOpt(reasonIndex));
        if (duration == null && reason[1] != null && !reason[1].isEmpty() && allowDuration) {
            long parsed = Durations.parse(reason[1]);
            if (parsed > 0) {
                duration = parsed;
            }
        }
        if (requireDuration && duration == null) {
            throw ctx.usage();
        }
        Long finalDuration = duration;
        String finalReason = reason[0];
        if (type == Punishment.Type.IPBAN && IP.matcher(name).matches()) {
            service.punish(new PunishmentService.Target(null, name, name), type, finalReason, finalDuration, ctx.sender)
                    .thenAccept(done -> ctx.send("punish.done.ipban", service.resolvers(done)));
            return;
        }
        Player online = plugin.players().findOnline(ctx.sender, name);
        if (online != null) {
            checkExempt(ctx, online);
        }
        service.resolve(ctx.sender, name).thenAccept(found -> {
            if (found.isEmpty()) {
                ctx.send("errors.player-never-joined", p("player", name));
                return;
            }
            PunishmentService.Target target = found.get();
            if (ctx.sender instanceof Player actor && actor.getUniqueId().equals(target.uuid())) {
                ctx.send("punish.self");
                return;
            }
            if (type == Punishment.Type.IPBAN && target.ip() == null) {
                ctx.send("punish.no-ip", p("player", target.name()));
                return;
            }
            CompletableFuture<Boolean> exempt = ctx.sender instanceof Player
                    ? service.exempt(target.uuid())
                    : CompletableFuture.completedFuture(false);
            exempt.thenCompose(blocked -> {
                if (blocked) {
                    ctx.send("punish.exempt", p("player", target.name()));
                    return CompletableFuture.completedFuture(null);
                }
                return service.punish(target, type, finalReason, finalDuration, ctx.sender)
                        .thenAccept(done -> ctx.send("punish.done." + type.key(), service.resolvers(done)));
            }).exceptionally(e -> {
                plugin.getLogger().warning("Punishment failed: " + e);
                ctx.send("errors.internal");
                return null;
            });
        });
    }

    private void unban(Ctx ctx) {
        String name = ctx.arg(0);
        if (IP.matcher(name).matches()) {
            service.revoke(null, name, List.of(Punishment.Type.IPBAN), ctx.sender)
                    .thenAccept(count -> ctx.send(count > 0 ? "punish.unbanned" : "punish.not-banned", p("player", name)));
            return;
        }
        service.resolve(ctx.sender, name).thenAccept(found -> {
            if (found.isEmpty()) {
                ctx.send("errors.player-never-joined", p("player", name));
                return;
            }
            PunishmentService.Target target = found.get();
            service.revoke(target.uuid(), target.ip(), List.of(Punishment.Type.BAN, Punishment.Type.IPBAN), ctx.sender)
                    .thenAccept(count -> ctx.send(count > 0 ? "punish.unbanned" : "punish.not-banned", p("player", target.name())));
        });
    }

    private void unmute(Ctx ctx) {
        String name = ctx.arg(0);
        service.resolve(ctx.sender, name).thenAccept(found -> {
            if (found.isEmpty()) {
                ctx.send("errors.player-never-joined", p("player", name));
                return;
            }
            PunishmentService.Target target = found.get();
            service.revoke(target.uuid(), null, List.of(Punishment.Type.MUTE), ctx.sender).thenAccept(count -> {
                ctx.send(count > 0 ? "punish.unmuted" : "punish.not-muted", p("player", target.name()));
                Player player = Bukkit.getPlayer(target.uuid());
                if (count > 0 && player != null) {
                    plugin.msg().send(player, "punish.unmuted-notify");
                }
            });
        });
    }

    private void warnings(Ctx ctx) {
        String name;
        if (ctx.present(0)) {
            name = ctx.arg(0);
            if (!(ctx.sender instanceof Player player) || !player.getName().equalsIgnoreCase(name)) {
                ctx.require("anyplugin.warnings.others");
            }
        } else {
            name = ctx.player().getName();
        }
        service.resolve(ctx.sender, name).thenAccept(found -> {
            if (found.isEmpty()) {
                ctx.send("errors.player-never-joined", p("player", name));
                return;
            }
            service.activeWarns(found.get().uuid()).thenAccept(list -> {
                if (list.isEmpty()) {
                    ctx.send("punish.warnings-none", p("player", found.get().name()));
                    return;
                }
                ctx.send("punish.warnings-header", p("player", found.get().name()), p("count", list.size()));
                for (Punishment warn : list) {
                    ctx.send("punish.warnings-line", service.resolvers(warn));
                }
            });
        });
    }

    private void history(Ctx ctx) {
        String name = ctx.arg(0);
        service.resolve(ctx.sender, name).thenAccept(found -> {
            if (found.isEmpty()) {
                ctx.send("errors.player-never-joined", p("player", name));
                return;
            }
            service.history(found.get().uuid(), 15).thenAccept(list -> {
                if (list.isEmpty()) {
                    ctx.send("punish.history-none", p("player", found.get().name()));
                    return;
                }
                ctx.send("punish.history-header", p("player", found.get().name()));
                long now = System.currentTimeMillis();
                for (Punishment item : list) {
                    String status;
                    if (item.type() == Punishment.Type.KICK) {
                        status = plugin.msg().raw("punish.status.done");
                    } else if (item.removedBy() != null) {
                        status = plugin.msg().raw("punish.status.removed").replace("<actor>", item.removedBy());
                    } else if (item.expired(now)) {
                        status = plugin.msg().raw("punish.status.expired");
                    } else {
                        status = plugin.msg().raw("punish.status.active");
                    }
                    ctx.send("punish.history-line", service.resolvers(item), p("status", status));
                }
            });
        });
    }
}
