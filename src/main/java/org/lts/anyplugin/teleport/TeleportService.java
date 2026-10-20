package org.lts.anyplugin.teleport;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.core.Locations;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.p;

public final class TeleportService implements Listener {

    private record Pending(ScheduledTask task, int x, int y, int z) {
    }

    private final AnyPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private volatile String spawnRaw;

    public TeleportService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.db().one("SELECT value FROM kv WHERE key='spawn'", rs -> rs.getString("value"))
                .thenAccept(value -> spawnRaw = value.orElse(null));
    }

    public Location spawn() {
        Location location = Locations.deserialize(spawnRaw);
        if (location == null) {
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            return world == null ? null : world.getSpawnLocation().add(0.5, 0, 0.5);
        }
        return location;
    }

    public void setSpawn(Location location) {
        spawnRaw = Locations.serialize(location);
        plugin.db().update("INSERT OR REPLACE INTO kv (key, value) VALUES ('spawn', ?)", spawnRaw);
    }

    public long cooldownLeft(Player player, String key) {
        if (key == null || player.hasPermission("anyplugin.teleport.bypass-cooldown")) {
            return 0;
        }
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) {
            return 0;
        }
        Long until = map.get(key);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    private void startCooldown(Player player, String key) {
        if (key == null) {
            return;
        }
        long seconds = plugin.getConfig().getLong("teleport.cooldowns." + key, 0);
        if (seconds > 0) {
            cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(key, System.currentTimeMillis() + seconds * 1000);
        }
    }

    public void teleport(Player player, Location destination, String cooldownKey, String successKey, TagResolver... resolvers) {
        teleport(player, () -> CompletableFuture.completedFuture(destination), cooldownKey, successKey, resolvers);
    }

    public void teleport(Player player, Supplier<CompletableFuture<Location>> destination, String cooldownKey, String successKey, TagResolver... resolvers) {
        long left = cooldownLeft(player, cooldownKey);
        if (left > 0) {
            throw new CmdException("teleport.cooldown", p("time", plugin.msg().duration(left)));
        }
        cancelPending(player, null);
        int warmup = plugin.getConfig().getInt("teleport.warmup-seconds", 3);
        if (warmup <= 0 || player.hasPermission("anyplugin.teleport.bypass-warmup")) {
            execute(player, destination, cooldownKey, successKey, resolvers);
            return;
        }
        Location here = player.getLocation();
        ScheduledTask task = player.getScheduler().runDelayed(plugin, t -> {
            pending.remove(player.getUniqueId());
            execute(player, destination, cooldownKey, successKey, resolvers);
        }, null, warmup * 20L);
        if (task != null) {
            pending.put(player.getUniqueId(), new Pending(task, here.getBlockX(), here.getBlockY(), here.getBlockZ()));
            plugin.msg().send(player, "teleport.warmup", p("seconds", warmup));
        }
    }

    private void execute(Player player, Supplier<CompletableFuture<Location>> destination, String cooldownKey, String successKey, TagResolver... resolvers) {
        destination.get().whenComplete((location, error) -> {
            if (error != null || location == null || location.getWorld() == null) {
                plugin.msg().send(player, "teleport.failed");
                return;
            }
            plugin.tasks().entity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Location from = player.getLocation();
                player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> {
                    if (ok) {
                        plugin.players().data(player).back = from;
                        startCooldown(player, cooldownKey);
                        if (successKey != null) {
                            plugin.msg().send(player, successKey, resolvers);
                        }
                    } else {
                        plugin.msg().send(player, "teleport.failed");
                    }
                });
            });
        });
    }

    public void cancelPending(Player player, String reasonKey) {
        Pending removed = pending.remove(player.getUniqueId());
        if (removed != null) {
            removed.task().cancel();
            if (reasonKey != null) {
                plugin.msg().send(player, reasonKey);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Pending current = pending.get(event.getPlayer().getUniqueId());
        if (current == null) {
            return;
        }
        Location to = event.getTo();
        if (to.getBlockX() != current.x() || to.getBlockY() != current.y() || to.getBlockZ() != current.z()) {
            cancelPending(event.getPlayer(), "teleport.cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && pending.containsKey(player.getUniqueId())) {
            cancelPending(player, "teleport.cancelled-damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            plugin.players().data(event.getPlayer()).back = event.getFrom().clone();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getPlayer().hasPermission("anyplugin.back.death")) {
            plugin.players().data(event.getPlayer()).back = event.getPlayer().getLocation().clone();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer(), null);
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn() || !plugin.getConfig().getBoolean("spawn.respawn-at-spawn", true)) {
            return;
        }
        Location spawn = spawnRaw == null ? null : spawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().data(player);
        boolean first = data.isNew && plugin.getConfig().getBoolean("spawn.teleport-on-first-join", true);
        if ((first || plugin.getConfig().getBoolean("spawn.teleport-on-every-join", false)) && spawnRaw != null) {
            Location spawn = spawn();
            if (spawn != null) {
                plugin.tasks().entityLater(player, 2, () -> player.teleportAsync(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN));
            }
        }
    }

    public void register(CommandRegistry registry) {
        registry.cmd("spawn").perm("anyplugin.spawn").exec(ctx -> {
            Player target = ctx.targetOrSelf(0, "anyplugin.spawn.others");
            Location spawn = spawn();
            if (spawn == null) {
                throw new CmdException("spawn.not-set");
            }
            if (target != ctx.sender) {
                instant(target, spawn);
                ctx.send("spawn.sent", p("player", target.getName()));
                return;
            }
            teleport(target, spawn, "spawn", "spawn.teleported");
        }).tab(ctx -> ctx.index() == 0 && ctx.has("anyplugin.spawn.others") ? CommandRegistry.players(ctx.sender) : List.of());
        registry.cmd("setspawn").perm("anyplugin.setspawn").playerOnly().logged().exec(ctx -> {
            Player player = ctx.player();
            Location location = player.getLocation();
            setSpawn(location);
            player.getWorld().setSpawnLocation(location);
            ctx.send("spawn.set", p("location", Locations.brief(location)));
        });
        registry.cmd("back").alias("return").perm("anyplugin.back").playerOnly().exec(ctx -> {
            Player player = ctx.player();
            Location back = plugin.players().data(player).back;
            if (back == null || back.getWorld() == null) {
                throw new CmdException("back.none");
            }
            teleport(player, back.clone(), "back", "back.teleported");
        });
        registry.cmd("tphere").alias("s").perm("anyplugin.tphere").playerOnly().logged().exec(ctx -> {
            Player target = ctx.online(0);
            Location here = ctx.player().getLocation();
            instant(target, here);
            ctx.send("tp.here", p("player", target.getName()));
        }).tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
        registry.cmd("tpall").perm("anyplugin.tpall").playerOnly().logged().exec(ctx -> {
            Player player = ctx.player();
            Location here = player.getLocation();
            int count = 0;
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    instant(other, here);
                    count++;
                }
            }
            ctx.send("tp.all", p("count", count));
        });
        registry.cmd("tppos").perm("anyplugin.tppos").logged().exec(this::tppos)
                .tab(ctx -> ctx.index() == 3 ? Bukkit.getWorlds().stream().map(World::getName).toList() : List.of("~"));
        registry.cmd("top").perm("anyplugin.top").playerOnly().exec(ctx -> {
            Player player = ctx.player();
            Location location = player.getLocation();
            Block highest = location.getWorld().getHighestBlockAt(location);
            Location target = highest.getLocation().add(0.5, 1, 0.5);
            target.setYaw(location.getYaw());
            target.setPitch(location.getPitch());
            instant(player, target);
            ctx.send("tp.top");
        });
    }

    private void tppos(Ctx ctx) {
        Player player = ctx.present(4) ? ctx.online(4) : ctx.player();
        Location base = player.getLocation();
        double x = coordinate(ctx, 0, base.getX());
        double y = coordinate(ctx, 1, base.getY());
        double z = coordinate(ctx, 2, base.getZ());
        World world = base.getWorld();
        if (ctx.present(3)) {
            world = Bukkit.getWorld(ctx.arg(3));
            if (world == null) {
                throw new CmdException("errors.world-not-found", p("world", ctx.arg(3)));
            }
        }
        Location target = new Location(world, x, y, z, base.getYaw(), base.getPitch());
        instant(player, target);
        ctx.send("tp.pos", p("location", Locations.brief(target)));
    }

    private static double coordinate(Ctx ctx, int i, double base) {
        String raw = ctx.arg(i);
        if (raw.startsWith("~")) {
            return base + (raw.length() > 1 ? parse(ctx, raw.substring(1)) : 0);
        }
        return parse(ctx, raw);
    }

    private static double parse(Ctx ctx, String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw ctx.fail("errors.number", p("value", raw));
        }
    }

    public void instant(Player player, Location location) {
        plugin.tasks().entity(player, () -> {
            Location from = player.getLocation();
            player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> {
                if (ok) {
                    plugin.players().data(player).back = from;
                }
            });
        });
    }
}
