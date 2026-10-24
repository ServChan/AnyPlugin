package org.lts.anyplugin.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.core.Tasks;

public final class ViewGuard implements Listener {

    private static final class Snapshot implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class Trash implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final AnyPlugin plugin;
    private final Map<UUID, Inventory> readOnly = new ConcurrentHashMap<>();

    public ViewGuard(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void openOther(Player viewer, Player owner, Inventory live, Component title, boolean editable) {
        boolean sameRegion = !Tasks.FOLIA || Bukkit.isOwnedByCurrentRegion(owner);
        if (sameRegion) {
            viewer.openInventory(live);
            if (!editable) {
                readOnly.put(viewer.getUniqueId(), live);
            }
            return;
        }
        plugin.tasks().entity(owner, () -> {
            ItemStack[] contents = live.getContents().clone();
            for (int i = 0; i < contents.length; i++) {
                contents[i] = contents[i] == null ? null : contents[i].clone();
            }
            plugin.tasks().entity(viewer, () -> {
                Snapshot holder = new Snapshot();
                int size = Math.min(54, (contents.length + 8) / 9 * 9);
                Inventory copy = Bukkit.createInventory(holder, size, title.append(plugin.msg().get("invsee.snapshot-suffix")));
                holder.inventory = copy;
                for (int i = 0; i < Math.min(size, contents.length); i++) {
                    copy.setItem(i, contents[i]);
                }
                viewer.openInventory(copy);
            });
        });
    }

    public void openTrash(Player player) {
        Trash holder = new Trash();
        Inventory inventory = Bukkit.createInventory(holder, 36, plugin.msg().get("utility.trash-title"));
        holder.inventory = inventory;
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder(false) instanceof Snapshot) {
            event.setCancelled(true);
            return;
        }
        Inventory locked = readOnly.get(event.getWhoClicked().getUniqueId());
        if (locked != null && locked.equals(top)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        Inventory locked = readOnly.get(event.getWhoClicked().getUniqueId());
        if (top.getHolder(false) instanceof Snapshot || (locked != null && locked.equals(top))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        readOnly.remove(event.getPlayer().getUniqueId());
        if (event.getInventory().getHolder(false) instanceof Trash) {
            event.getInventory().clear();
        }
    }
}
