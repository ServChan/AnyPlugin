package org.lts.anyplugin.util;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.c;

public final class AfkService implements Listener {

    private final AnyPlugin plugin;

    public AfkService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.tasks().globalTimer(100, 100, this::check);
    }

    public void register(CommandRegistry registry) {
        registry.cmd("afk").alias("away").perm("anyplugin.afk").playerOnly().exec(ctx -> {
            Player player = ctx.player();
            PlayerData data = plugin.players().data(player);
            setAfk(player, !data.afk);
            if (!data.afk) {
                data.lastActivity = System.currentTimeMillis();
            }
        });
    }

    private void check() {
        long timeout = plugin.getConfig().getLong("afk.auto-seconds", 300) * 1000;
        long kickAfter = plugin.getConfig().getLong("afk.kick-seconds", 0) * 1000;
        if (timeout <= 0 && kickAfter <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.players().data(player.getUniqueId());
            if (data == null) {
                continue;
            }
            long idle = now - data.lastActivity;
            if (timeout > 0 && !data.afk && idle >= timeout && !player.hasPermission("anyplugin.afk.exempt")) {
                plugin.tasks().entity(player, () -> setAfk(player, true));
            }
            if (kickAfter > 0 && idle >= kickAfter && !player.hasPermission("anyplugin.afk.kick-exempt")) {
                plugin.tasks().entity(player, () -> player.kick(plugin.msg().get("afk.kick")));
            }
        }
    }

    public void setAfk(Player player, boolean afk) {
        PlayerData data = plugin.players().data(player);
        if (data.afk == afk) {
            return;
        }
        data.afk = afk;
        player.setSleepingIgnored(afk || data.vanished);
        if (!data.vanished && plugin.getConfig().getBoolean("afk.broadcast", true)) {
            Bukkit.getServer().sendMessage(plugin.msg().get(afk ? "afk.now" : "afk.back", c("player", player.displayName())));
        } else {
            plugin.msg().send(player, afk ? "afk.now-self" : "afk.back-self");
        }
        if (plugin.tab() != null) {
            plugin.tab().refresh(player);
        }
    }

    private void active(Player player) {
        PlayerData data = plugin.players().data(player.getUniqueId());
        if (data == null) {
            return;
        }
        data.lastActivity = System.currentTimeMillis();
        if (data.afk) {
            plugin.tasks().entity(player, () -> setAfk(player, false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (Math.abs(from.getYaw() - to.getYaw()) > 2 || Math.abs(from.getPitch() - to.getPitch()) > 2) {
            active(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String lower = event.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("/afk") && !lower.startsWith("/away")) {
            active(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            active(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = plugin.players().data(player.getUniqueId());
            if (data != null && data.god && event.getCause() != EntityDamageEvent.DamageCause.KILL
                    && event.getCause() != EntityDamageEvent.DamageCause.WORLD_BORDER) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = plugin.players().data(player.getUniqueId());
            if (data != null && data.god && event.getFoodLevel() < player.getFoodLevel()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        restoreFlight(event.getPlayer(), 5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        restoreFlight(event.getPlayer(), 2);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SURVIVAL || event.getNewGameMode() == GameMode.ADVENTURE) {
            restoreFlight(event.getPlayer(), 2);
        }
    }

    private void restoreFlight(Player player, long delay) {
        PlayerData data = plugin.players().data(player.getUniqueId());
        if (data == null || !data.fly) {
            return;
        }
        plugin.tasks().entityLater(player, delay, () -> {
            if (player.isOnline() && player.hasPermission("anyplugin.fly")) {
                player.setAllowFlight(true);
            }
        });
    }
}
