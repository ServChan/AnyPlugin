package org.lts.anyplugin.teleport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.core.Locations;
import org.lts.anyplugin.core.Text;

import static org.lts.anyplugin.core.Messages.p;

public final class WarpService implements Listener {

    public record Warp(String name, String world, double x, double y, double z, float yaw, float pitch, String icon, String description) {

        Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private static final class Menu implements InventoryHolder {
        private final List<Warp> slots;
        private Inventory inventory;

        private Menu(List<Warp> slots) {
            this.slots = slots;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final AnyPlugin plugin;
    private final TeleportService teleports;
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();

    public WarpService(AnyPlugin plugin, TeleportService teleports) {
        this.plugin = plugin;
        this.teleports = teleports;
    }

    public void start() {
        plugin.db().list("SELECT * FROM warps", rs -> new Warp(rs.getString("name"), rs.getString("world"), rs.getDouble("x"),
                rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"), rs.getString("icon"),
                rs.getString("description"))).thenAccept(list -> list.forEach(w -> warps.put(w.name(), w)));
    }

    public void register(CommandRegistry registry) {
        registry.cmd("warp").perm("anyplugin.warp").exec(this::warp).tab(ctx -> {
            if (ctx.index() == 0) {
                return visibleNames(ctx);
            }
            return ctx.index() == 1 && ctx.has("anyplugin.warp.others") ? CommandRegistry.players(ctx.sender) : List.of();
        });
        registry.cmd("warps").perm("anyplugin.warp").exec(ctx -> {
            if (ctx.isPlayer()) {
                open(ctx.player());
            } else if (warps.isEmpty()) {
                ctx.send("warp.none");
            } else {
                ctx.send("warp.list", p("warps", String.join(", ", sortedNames())));
            }
        });
        registry.cmd("setwarp").alias("createwarp").perm("anyplugin.setwarp").playerOnly().logged().exec(this::setWarp)
                .tab(ctx -> ctx.index() == 1 ? iconSuggestions() : List.of());
        registry.cmd("delwarp").alias("removewarp").perm("anyplugin.delwarp").logged().exec(ctx -> {
            String key = ctx.arg(0).toLowerCase(Locale.ROOT);
            if (warps.remove(key) == null) {
                throw new CmdException("warp.not-found", p("name", ctx.arg(0)));
            }
            plugin.db().update("DELETE FROM warps WHERE name=?", key);
            ctx.send("warp.deleted", p("name", key));
        }).tab(ctx -> ctx.index() == 0 ? sortedNames() : List.of());
    }

    public java.util.Collection<Warp> all() {
        return warps.values();
    }

    private boolean canUse(org.bukkit.command.CommandSender sender, Warp warp) {
        if (!plugin.getConfig().getBoolean("warps.per-warp-permission", false)) {
            return true;
        }
        return sender.hasPermission("anyplugin.warp." + warp.name()) || sender.hasPermission("anyplugin.warp.all");
    }

    private List<String> sortedNames() {
        List<String> names = new ArrayList<>(warps.keySet());
        names.sort(null);
        return names;
    }

    private List<String> visibleNames(Ctx ctx) {
        List<String> names = new ArrayList<>();
        for (Warp warp : warps.values()) {
            if (canUse(ctx.sender, warp)) {
                names.add(warp.name());
            }
        }
        names.sort(null);
        return names;
    }

    private static List<String> iconSuggestions() {
        return List.of("grass_block", "oak_door", "chest", "diamond", "nether_star", "beacon", "compass", "ender_pearl", "lantern", "campfire");
    }

    private void warp(Ctx ctx) {
        if (!ctx.present(0)) {
            if (ctx.isPlayer()) {
                open(ctx.player());
            } else {
                ctx.send("warp.list", p("warps", String.join(", ", sortedNames())));
            }
            return;
        }
        Warp warp = warps.get(ctx.arg(0).toLowerCase(Locale.ROOT));
        if (warp == null || !canUse(ctx.sender, warp)) {
            throw new CmdException("warp.not-found", p("name", ctx.arg(0)));
        }
        Location location = warp.location();
        if (location == null) {
            throw new CmdException("warp.world-missing", p("world", warp.world()));
        }
        if (ctx.present(1)) {
            ctx.require("anyplugin.warp.others");
            Player target = ctx.online(1);
            teleports.instant(target, location);
            plugin.msg().send(target, "warp.teleported", p("name", warp.name()));
            ctx.send("warp.sent", p("name", warp.name()), p("player", target.getName()));
            return;
        }
        teleports.teleport(ctx.player(), location, "warp", "warp.teleported", p("name", warp.name()));
    }

    private void setWarp(Ctx ctx) {
        Player player = ctx.player();
        String name = ctx.arg(0).toLowerCase(Locale.ROOT);
        if (!name.matches("[\\p{L}0-9_\\-]{1,24}")) {
            throw new CmdException("warp.bad-name");
        }
        String icon = ctx.opt(1, plugin.getConfig().getString("warps.default-icon", "ender_pearl")).toLowerCase(Locale.ROOT);
        if (Material.matchMaterial(icon) == null) {
            throw new CmdException("warp.bad-icon", p("icon", icon));
        }
        String description = ctx.joinedOpt(2);
        Location l = player.getLocation();
        Warp warp = new Warp(name, l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(), icon, description);
        boolean existed = warps.put(name, warp) != null;
        plugin.db().update("INSERT OR REPLACE INTO warps (name, world, x, y, z, yaw, pitch, icon, description, created_by, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                name, warp.world(), warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch(), icon, description, player.getName(), System.currentTimeMillis());
        ctx.send(existed ? "warp.updated" : "warp.created", p("name", name), p("location", Locations.brief(l)));
    }

    private void open(Player player) {
        List<Warp> list = new ArrayList<>();
        for (Warp warp : warps.values()) {
            if (canUse(player, warp)) {
                list.add(warp);
            }
        }
        if (list.isEmpty()) {
            plugin.msg().send(player, "warp.none");
            return;
        }
        list.sort(Comparator.comparing(Warp::name));
        if (list.size() > 54) {
            list = new ArrayList<>(list.subList(0, 54));
        }
        int rows = Math.max(1, (list.size() + 8) / 9);
        Menu menu = new Menu(list);
        Inventory inventory = Bukkit.createInventory(menu, rows * 9, plugin.msg().get("warp.menu-title"));
        menu.inventory = inventory;
        for (int i = 0; i < list.size(); i++) {
            inventory.setItem(i, icon(list.get(i)));
        }
        player.openInventory(inventory);
    }

    private ItemStack icon(Warp warp) {
        Material material = warp.icon() == null ? null : Material.matchMaterial(warp.icon());
        if (material == null || !material.isItem() || material.isAir()) {
            material = Material.ENDER_PEARL;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.msg().get("warp.menu-name", p("name", warp.name())).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        List<Component> lore = new ArrayList<>();
        if (warp.description() != null && !warp.description().isEmpty()) {
            lore.add(Text.userInput(warp.description(), true).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        lore.add(plugin.msg().get("warp.menu-location", p("world", warp.world()), p("x", (int) warp.x()), p("y", (int) warp.y()), p("z", (int) warp.z()))
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        lore.add(plugin.msg().get("warp.menu-click").decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();
        if (slot < 0 || slot >= menu.slots.size()) {
            return;
        }
        Warp warp = menu.slots.get(slot);
        player.closeInventory();
        plugin.tasks().entityLater(player, 1, () -> {
            try {
                player.performCommand("warp " + warp.name());
            } catch (RuntimeException e) {
                plugin.msg().send(player, "teleport.failed");
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
