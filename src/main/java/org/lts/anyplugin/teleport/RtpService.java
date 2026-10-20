package org.lts.anyplugin.teleport;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;

import static org.lts.anyplugin.core.Messages.p;

public final class RtpService {

    private static final Set<Material> UNSAFE = Set.of(Material.LAVA, Material.WATER, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS,
            Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
            Material.POINTED_DRIPSTONE, Material.BUBBLE_COLUMN, Material.KELP, Material.SEAGRASS, Material.TALL_SEAGRASS);

    private final AnyPlugin plugin;
    private final TeleportService teleports;

    public RtpService(AnyPlugin plugin, TeleportService teleports) {
        this.plugin = plugin;
        this.teleports = teleports;
    }

    public void register(CommandRegistry registry) {
        registry.cmd("rtp").alias("wild", "randomtp").perm("anyplugin.rtp").exec(this::rtp)
                .tab(ctx -> ctx.index() == 0 && ctx.has("anyplugin.rtp.others") ? CommandRegistry.players(ctx.sender) : List.of());
    }

    private void rtp(Ctx ctx) {
        Player player = ctx.targetOrSelf(0, "anyplugin.rtp.others");
        FileConfiguration cfg = plugin.getConfig();
        World world = player.getWorld();
        List<String> worlds = cfg.getStringList("rtp.worlds");
        if (!worlds.isEmpty() && !worlds.contains(world.getName())) {
            String fallback = cfg.getString("rtp.fallback-world", "");
            World other = fallback.isEmpty() ? null : Bukkit.getWorld(fallback);
            if (other == null) {
                throw new CmdException("rtp.world-blocked");
            }
            world = other;
        }
        if (world.getEnvironment() != World.Environment.NORMAL && !cfg.getBoolean("rtp.allow-nether-end", false)) {
            throw new CmdException("rtp.world-blocked");
        }
        World target = world;
        boolean self = player == ctx.sender;
        if (self) {
            ctx.send("rtp.searching");
            teleports.teleport(player, () -> find(target, cfg.getInt("rtp.attempts", 12)), "rtp", "rtp.teleported");
        } else {
            find(target, cfg.getInt("rtp.attempts", 12)).thenAccept(location -> {
                if (location == null) {
                    ctx.send("rtp.failed");
                } else {
                    teleports.instant(player, location);
                    ctx.send("rtp.sent", p("player", player.getName()));
                }
            });
        }
    }

    private CompletableFuture<Location> find(World world, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        FileConfiguration cfg = plugin.getConfig();
        int min = cfg.getInt("rtp.min-radius", 300);
        int max = Math.max(min + 1, cfg.getInt("rtp.max-radius", 5000));
        Location center = cfg.getBoolean("rtp.center-on-spawn", true) ? world.getSpawnLocation() : new Location(world, 0, 0, 0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        double distance = min + random.nextDouble() * (max - min);
        int x = center.getBlockX() + (int) (Math.cos(angle) * distance);
        int z = center.getBlockZ() + (int) (Math.sin(angle) * distance);
        double border = world.getWorldBorder().getSize() / 2 - 16;
        Location borderCenter = world.getWorldBorder().getCenter();
        if (Math.abs(x - borderCenter.getX()) > border || Math.abs(z - borderCenter.getZ()) > border) {
            return find(world, attemptsLeft - 1);
        }
        CompletableFuture<Location> result = new CompletableFuture<>();
        world.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, error) -> {
            if (error != null || chunk == null) {
                find(world, attemptsLeft - 1).thenAccept(result::complete);
                return;
            }
            plugin.tasks().region(world, x >> 4, z >> 4, () -> {
                Location safe = safeAt(world, x, z);
                if (safe != null) {
                    result.complete(safe);
                } else {
                    find(world, attemptsLeft - 1).thenAccept(result::complete);
                }
            });
        });
        return result;
    }

    private static Location safeAt(World world, int x, int z) {
        Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
        if (ground.getY() <= world.getMinHeight() + 1) {
            return null;
        }
        Material type = ground.getType();
        if (UNSAFE.contains(type) || ground.isLiquid() || !type.isSolid() && !type.name().endsWith("LEAVES")) {
            return null;
        }
        Block feet = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);
        if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) {
            return null;
        }
        return new Location(world, x + 0.5, ground.getY() + 1, z + 0.5, ThreadLocalRandom.current().nextFloat() * 360f - 180f, 0f);
    }
}
