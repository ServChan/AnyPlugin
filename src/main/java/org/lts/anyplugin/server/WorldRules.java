package org.lts.anyplugin.server;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.lts.anyplugin.AnyPlugin;

public final class WorldRules implements Listener {

    private final AnyPlugin plugin;

    public WorldRules(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void start() {
        plugin.tasks().global(() -> Bukkit.getWorlds().forEach(this::applyRules));
    }

    private void applyRules(World world) {
        int percent = cfg().getInt("world.sleep-percentage", -1);
        if (percent >= 0) {
            try {
                world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, percent);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Could not set sleep percentage in " + world.getName() + ": " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.tasks().global(() -> applyRules(event.getWorld()));
    }

    private boolean protectedAt(Location location) {
        int radius = cfg().getInt("world.spawn-protection.radius", 0);
        if (radius <= 0 || plugin.teleports() == null) {
            return false;
        }
        Location spawn = plugin.teleports().spawn();
        if (spawn == null || !spawn.getWorld().equals(location.getWorld())) {
            return false;
        }
        return Math.abs(location.getBlockX() - spawn.getBlockX()) <= radius && Math.abs(location.getBlockZ() - spawn.getBlockZ()) <= radius;
    }

    private boolean denied(Player player, Location location) {
        if (player.hasPermission("anyplugin.spawnprotection.bypass") || !protectedAt(location)) {
            return false;
        }
        plugin.msg().send(player, "world.spawn-protected");
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (!cfg().getBoolean("world.pvp", true) || cfg().getBoolean("world.spawn-protection.no-pvp", true) && protectedAt(victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        boolean creeper = event.getEntity() instanceof Creeper;
        if (creeper && !cfg().getBoolean("world.creeper-block-damage", true)
                || !creeper && event.getEntityType() != EntityType.TNT && event.getEntityType() != EntityType.TNT_MINECART
                && !cfg().getBoolean("world.mob-explosion-block-damage", true)) {
            event.blockList().clear();
            return;
        }
        event.blockList().removeIf(block -> protectedAt(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderman(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman && !cfg().getBoolean("world.enderman-grief", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (!cfg().getBoolean("world.fire-spread", true) && event.getNewState().getType().name().endsWith("FIRE")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!cfg().getBoolean("world.fire-spread", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!cfg().getBoolean("world.fire-spread", true) && event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) {
            event.setCancelled(true);
        }
        if (event.getPlayer() == null && protectedAt(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.PHANTOM && !cfg().getBoolean("world.phantoms", true)) {
            event.setCancelled(true);
        }
    }
}
