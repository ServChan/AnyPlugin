package org.lts.anyplugin.core;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class Tasks {

    public static final boolean FOLIA = detectFolia();

    private final Plugin plugin;

    public Tasks(Plugin plugin) {
        this.plugin = plugin;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void global(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public ScheduledTask globalLater(long ticks, Runnable task) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, ticks));
    }

    public ScheduledTask globalTimer(long delayTicks, long periodTicks, Runnable task) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public void entity(Entity entity, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
        } else {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        }
    }

    public void entityLater(Entity entity, long ticks, Runnable task) {
        entity.getScheduler().runDelayed(plugin, t -> task.run(), null, Math.max(1, ticks));
    }

    public ScheduledTask entityTimer(Entity entity, long delayTicks, long periodTicks, Runnable task) {
        return entity.getScheduler().runAtFixedRate(plugin, t -> task.run(), null, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public void region(Location location, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
        } else {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        }
    }

    public void region(World world, int chunkX, int chunkZ, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            task.run();
        } else {
            Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, task);
        }
    }

    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    public ScheduledTask asyncTimer(long delayMillis, long periodMillis, Runnable task) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1, delayMillis), Math.max(1, periodMillis), TimeUnit.MILLISECONDS);
    }

    public void forEachOnline(Consumer<Player> action) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            entity(player, () -> {
                if (player.isOnline()) {
                    action.accept(player);
                }
            });
        }
    }

    public CompletableFuture<Location> locationOf(Entity entity) {
        CompletableFuture<Location> future = new CompletableFuture<>();
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            future.complete(entity.getLocation());
            return future;
        }
        ScheduledTask scheduled = entity.getScheduler().run(plugin, t -> future.complete(entity.getLocation()), () -> future.complete(null));
        if (scheduled == null) {
            future.complete(null);
        }
        return future;
    }

    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }
}
