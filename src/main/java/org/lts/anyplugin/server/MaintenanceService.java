package org.lts.anyplugin.server;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.player.PlayerData;

public final class MaintenanceService {

    private final AnyPlugin plugin;
    private volatile boolean enabled;

    public MaintenanceService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.db().one("SELECT value FROM kv WHERE key='maintenance'", rs -> rs.getString("value"))
                .thenAccept(value -> enabled = value.map("1"::equals).orElse(false));
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean canJoin(UUID uuid, PlayerData data) {
        if (Bukkit.getWhitelistedPlayers().stream().anyMatch(p -> p.getUniqueId().equals(uuid))
                && plugin.getConfig().getBoolean("maintenance.allow-whitelisted", true)) {
            return true;
        }
        return plugin.perms().hasOffline(uuid, data, "anyplugin.maintenance.bypass");
    }

    public void register(CommandRegistry registry) {
        registry.cmd("maintenance").alias("techworks").perm("anyplugin.maintenance").logged().exec(ctx -> {
            String action = ctx.opt(0, "status").toLowerCase(Locale.ROOT);
            switch (action) {
                case "on" -> {
                    set(true);
                    ctx.send("maintenance.enabled");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!player.hasPermission("anyplugin.maintenance.bypass")) {
                            plugin.tasks().entity(player, () -> player.kick(plugin.msg().get("maintenance.kick")));
                        }
                    }
                }
                case "off" -> {
                    set(false);
                    ctx.send("maintenance.disabled");
                }
                default -> ctx.send(enabled ? "maintenance.status-on" : "maintenance.status-off");
            }
        }).tab(ctx -> ctx.index() == 0 ? java.util.List.of("on", "off", "status") : java.util.List.of());
    }

    private void set(boolean value) {
        enabled = value;
        plugin.db().update("INSERT OR REPLACE INTO kv (key, value) VALUES ('maintenance', ?)", value ? "1" : "0");
    }
}
