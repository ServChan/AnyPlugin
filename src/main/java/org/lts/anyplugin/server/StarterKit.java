package org.lts.anyplugin.server;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.p;

public final class StarterKit implements Listener {

    private final AnyPlugin plugin;

    public StarterKit(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(CommandRegistry registry) {
        registry.cmd("starterkit").perm("anyplugin.starterkit.give").logged().exec(ctx -> {
            Player target = ctx.online(0);
            plugin.tasks().entity(target, () -> give(target));
            ctx.send("kit.given-other", p("player", target.getName()));
        }).tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : java.util.List.of());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().data(player);
        if (!data.isNew || data.kitReceived || !plugin.getConfig().getBoolean("starter-kit.enabled", true)) {
            return;
        }
        data.kitReceived = true;
        plugin.players().setFlag(data, "kit_received", true);
        plugin.tasks().entityLater(player, 20, () -> give(player));
    }

    private void give(Player player) {
        if (!player.isOnline()) {
            return;
        }
        for (String line : plugin.getConfig().getStringList("starter-kit.items")) {
            String[] parts = line.trim().split("\\s+");
            Material material = Material.matchMaterial(parts[0].toLowerCase(Locale.ROOT));
            if (material == null || !material.isItem() || material.isAir()) {
                plugin.getLogger().warning("Starter kit: unknown item " + parts[0]);
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
            ItemStack item = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
            player.getInventory().addItem(item).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        plugin.msg().send(player, "kit.received");
    }
}
