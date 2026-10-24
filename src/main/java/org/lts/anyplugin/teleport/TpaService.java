package org.lts.anyplugin.teleport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.c;
import static org.lts.anyplugin.core.Messages.p;

public final class TpaService implements Listener {

    private record Request(UUID from, UUID to, boolean here, long expires) {
    }

    private final AnyPlugin plugin;
    private final TeleportService teleports;
    private final Map<UUID, Map<UUID, Request>> incoming = new ConcurrentHashMap<>();

    public TpaService(AnyPlugin plugin, TeleportService teleports) {
        this.plugin = plugin;
        this.teleports = teleports;
    }

    public void register(CommandRegistry registry) {
        registry.cmd("tpa").alias("call", "tpask").perm("anyplugin.tpa").playerOnly().exec(ctx -> request(ctx, false))
                .tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
        registry.cmd("tpahere").perm("anyplugin.tpahere").playerOnly().exec(ctx -> request(ctx, true))
                .tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
        registry.cmd("tpaccept").alias("tpyes").perm("anyplugin.tpa").playerOnly().exec(ctx -> answer(ctx, true)).tab(this::requesters);
        registry.cmd("tpdeny").alias("tpno").perm("anyplugin.tpa").playerOnly().exec(ctx -> answer(ctx, false)).tab(this::requesters);
        registry.cmd("tpacancel").perm("anyplugin.tpa").playerOnly().exec(this::cancel);
        registry.cmd("tptoggle").perm("anyplugin.tptoggle").playerOnly().exec(ctx -> {
            PlayerData data = plugin.players().data(ctx.player());
            data.tpToggle = !data.tpToggle;
            plugin.players().setFlag(data, "tptoggle", data.tpToggle);
            ctx.send(data.tpToggle ? "tpa.toggle-off" : "tpa.toggle-on");
        });
    }

    private void request(Ctx ctx, boolean here) {
        Player player = ctx.player();
        Player target = ctx.online(0);
        if (target.equals(player)) {
            throw new CmdException("tpa.self");
        }
        PlayerData targetData = plugin.players().data(target);
        if (targetData.tpToggle && !player.hasPermission("anyplugin.tptoggle.bypass")) {
            throw new CmdException("tpa.target-disabled", p("player", target.getName()));
        }
        if (plugin.chat() != null && plugin.chat().ignores(target, player)) {
            throw new CmdException("tpa.target-disabled", p("player", target.getName()));
        }
        long left = teleports.cooldownLeft(player, "tpa");
        if (left > 0) {
            throw new CmdException("teleport.cooldown", p("time", plugin.msg().duration(left)));
        }
        long timeout = plugin.getConfig().getLong("teleport.tpa-timeout-seconds", 60) * 1000;
        Request request = new Request(player.getUniqueId(), target.getUniqueId(), here, System.currentTimeMillis() + timeout);
        Map<UUID, Request> requests = incoming.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>());
        synchronized (requests) {
            requests.remove(player.getUniqueId());
            requests.put(player.getUniqueId(), request);
        }
        String name = player.getName();
        target.sendMessage(plugin.msg().get(here ? "tpa.request-here" : "tpa.request",
                c("player", player.displayName()),
                p("seconds", timeout / 1000),
                c("accept", plugin.msg().button("tpa.button-accept", ClickEvent.runCommand("/tpaccept " + name), "tpa.button-accept-hover")),
                c("deny", plugin.msg().button("tpa.button-deny", ClickEvent.runCommand("/tpdeny " + name), "tpa.button-deny-hover"))));
        ctx.send("tpa.sent", p("player", target.getName()), p("seconds", timeout / 1000));
    }

    private Request take(Player target, String fromName) {
        Map<UUID, Request> map = incoming.get(target.getUniqueId());
        if (map == null) {
            return null;
        }
        synchronized (map) {
            long now = System.currentTimeMillis();
            map.values().removeIf(r -> r.expires() < now);
            if (map.isEmpty()) {
                return null;
            }
            if (fromName != null) {
                Player from = Bukkit.getPlayerExact(fromName);
                return from == null ? null : map.remove(from.getUniqueId());
            }
            UUID last = null;
            for (UUID key : map.keySet()) {
                last = key;
            }
            return map.remove(last);
        }
    }

    private void answer(Ctx ctx, boolean accept) {
        Player target = ctx.player();
        Request request = take(target, ctx.opt(0, null));
        if (request == null) {
            throw new CmdException("tpa.no-request");
        }
        Player requester = Bukkit.getPlayer(request.from());
        if (requester == null) {
            throw new CmdException("tpa.requester-offline");
        }
        if (!accept) {
            ctx.send("tpa.denied", p("player", requester.getName()));
            plugin.msg().send(requester, "tpa.denied-by", p("player", target.getName()));
            return;
        }
        ctx.send("tpa.accepted", p("player", requester.getName()));
        plugin.msg().send(requester, "tpa.accepted-by", p("player", target.getName()));
        if (request.here()) {
            Player mover = target;
            plugin.tasks().entity(mover, () -> safeTeleport(mover, requester));
        } else {
            plugin.tasks().entity(requester, () -> safeTeleport(requester, target));
        }
    }

    private void safeTeleport(Player mover, Player destination) {
        try {
            teleports.teleport(mover, () -> plugin.tasks().locationOf(destination), "tpa", "tpa.teleported", p("player", destination.getName()));
        } catch (CmdException e) {
            plugin.msg().send(mover, e.key(), e.resolvers());
        }
    }

    private void cancel(Ctx ctx) {
        Player player = ctx.player();
        int removed = 0;
        for (Map<UUID, Request> map : incoming.values()) {
            synchronized (map) {
                if (map.remove(player.getUniqueId()) != null) {
                    removed++;
                }
            }
        }
        ctx.send(removed > 0 ? "tpa.cancelled" : "tpa.no-outgoing");
    }

    private List<String> requesters(Ctx ctx) {
        if (!(ctx.sender instanceof Player player) || ctx.index() != 0) {
            return List.of();
        }
        Map<UUID, Request> map = incoming.get(player.getUniqueId());
        List<String> names = new ArrayList<>();
        if (map != null) {
            synchronized (map) {
                for (UUID id : map.keySet()) {
                    Player from = Bukkit.getPlayer(id);
                    if (from != null) {
                        names.add(from.getName());
                    }
                }
            }
        }
        return names;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        incoming.remove(id);
        for (Map<UUID, Request> map : incoming.values()) {
            synchronized (map) {
                map.remove(id);
            }
        }
    }
}
