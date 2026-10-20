package org.lts.anyplugin.moderation;

import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.chat.ChatService;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.c;
import static org.lts.anyplugin.core.Messages.p;

public final class VanishService implements Listener {

    private final AnyPlugin plugin;

    public VanishService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(CommandRegistry registry) {
        registry.cmd("vanish").alias("v").perm("anyplugin.vanish").logged().exec(ctx -> {
            Player target = ctx.targetOrSelf(0, "anyplugin.vanish.others");
            PlayerData data = plugin.players().data(target);
            set(target, !data.vanished, true);
            if (target != ctx.sender) {
                ctx.send(data.vanished ? "vanish.on-other" : "vanish.off-other", p("player", target.getName()));
            }
        }).tab(ctx -> ctx.index() == 0 && ctx.has("anyplugin.vanish.others") ? CommandRegistry.players(ctx.sender) : List.of());
    }

    public void set(Player player, boolean vanished, boolean announce) {
        PlayerData data = plugin.players().data(player);
        data.vanished = vanished;
        plugin.players().setFlag(data, "vanished", vanished);
        plugin.tasks().entity(player, () -> {
            player.setSleepingIgnored(vanished || data.afk);
            if (plugin.tab() != null) {
                plugin.tab().refresh(player);
            }
        });
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) {
                continue;
            }
            boolean hide = vanished && !viewer.hasPermission("anyplugin.vanish.see");
            plugin.tasks().entity(viewer, () -> visibility(viewer, player, hide));
        }
        plugin.msg().send(player, vanished ? "vanish.on" : "vanish.off");
        ChatService.broadcastPermission("anyplugin.vanish.see", plugin.msg().get(vanished ? "vanish.notify-on" : "vanish.notify-off", p("player", player.getName())));
        if (announce && plugin.getConfig().getBoolean("vanish.fake-messages", true)) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(player) && !viewer.hasPermission("anyplugin.vanish.see")) {
                    viewer.sendMessage(plugin.msg().get(vanished ? "quit.message" : "join.message", c("player", player.displayName())));
                }
            }
        }
    }

    private void visibility(Player viewer, Player target, boolean hide) {
        try {
            if (hide) {
                viewer.hidePlayer(plugin, target);
            } else {
                viewer.showPlayer(plugin, target);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Could not change visibility", e);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        PlayerData data = plugin.players().data(joined);
        boolean canSee = joined.hasPermission("anyplugin.vanish.see");
        if (data.vanished && !joined.hasPermission("anyplugin.vanish")) {
            data.vanished = false;
            plugin.players().setFlag(data, "vanished", false);
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(joined)) {
                continue;
            }
            PlayerData otherData = plugin.players().data(other.getUniqueId());
            if (otherData != null && otherData.vanished && !canSee) {
                visibility(joined, other, true);
            }
            if (data.vanished && !other.hasPermission("anyplugin.vanish.see")) {
                plugin.tasks().entity(other, () -> visibility(other, joined, true));
            }
        }
        if (data.vanished) {
            joined.setSleepingIgnored(true);
            plugin.msg().send(joined, "vanish.still");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player) {
            PlayerData data = plugin.players().data(player.getUniqueId());
            if (data != null && data.vanished) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getConfig().getBoolean("vanish.block-pickup", true)) {
            PlayerData data = plugin.players().data(player.getUniqueId());
            if (data != null && data.vanished) {
                event.setCancelled(true);
            }
        }
    }
}
