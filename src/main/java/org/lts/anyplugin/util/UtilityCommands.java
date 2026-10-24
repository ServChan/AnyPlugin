package org.lts.anyplugin.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.core.Locations;
import org.lts.anyplugin.core.Text;
import org.lts.anyplugin.perms.Group;
import org.lts.anyplugin.player.OfflineRecord;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.c;
import static org.lts.anyplugin.core.Messages.mm;
import static org.lts.anyplugin.core.Messages.p;

public final class UtilityCommands {

    private final AnyPlugin plugin;
    private final ViewGuard views;

    public UtilityCommands(AnyPlugin plugin, ViewGuard views) {
        this.plugin = plugin;
        this.views = views;
    }

    private static List<String> playersFirst(Ctx ctx, int index) {
        return ctx.index() == index ? CommandRegistry.players(ctx.sender) : List.of();
    }

    public void register(CommandRegistry r) {
        gameModes(r);
        r.cmd("fly").perm("anyplugin.fly").exec(this::fly).tab(ctx -> playersFirst(ctx, 0));
        r.cmd("speed").perm("anyplugin.speed").exec(this::speed).tab(ctx -> switch (ctx.index()) {
            case 0 -> List.of("1", "2", "3", "5", "10");
            case 1 -> List.of("walk", "fly");
            case 2 -> CommandRegistry.players(ctx.sender);
            default -> List.of();
        });
        r.cmd("god").alias("godmode").perm("anyplugin.god").exec(this::god).tab(ctx -> playersFirst(ctx, 0));
        r.cmd("heal").perm("anyplugin.heal").logged().exec(this::heal).tab(ctx -> playersFirst(ctx, 0));
        r.cmd("feed").alias("eat").perm("anyplugin.feed").exec(this::feed).tab(ctx -> playersFirst(ctx, 0));
        r.cmd("repair").alias("fix").perm("anyplugin.repair").playerOnly().exec(this::repair).tab(ctx -> ctx.index() == 0 ? List.of("all") : List.of());
        r.cmd("hat").alias("head").perm("anyplugin.hat").playerOnly().exec(this::hat);
        r.cmd("suicide").perm("anyplugin.suicide").playerOnly().exec(ctx -> {
            Player player = ctx.player();
            player.setHealth(0);
            ctx.send("utility.suicide");
        });
        workbench(r, "craft", "anyplugin.craft", MenuType.CRAFTING, "workbench", "wb");
        workbench(r, "anvil", "anyplugin.anvil", MenuType.ANVIL);
        workbench(r, "stonecutter", "anyplugin.stonecutter", MenuType.STONECUTTER);
        workbench(r, "grindstone", "anyplugin.grindstone", MenuType.GRINDSTONE);
        workbench(r, "smithing", "anyplugin.smithing", MenuType.SMITHING, "smithingtable");
        workbench(r, "loom", "anyplugin.loom", MenuType.LOOM);
        workbench(r, "cartography", "anyplugin.cartography", MenuType.CARTOGRAPHY_TABLE, "cartographytable");
        r.cmd("enderchest").alias("ec", "echest").perm("anyplugin.enderchest").playerOnly().exec(this::enderChest).tab(ctx -> {
            return ctx.index() == 0 && ctx.has("anyplugin.enderchest.others") ? CommandRegistry.players(ctx.sender) : List.of();
        });
        r.cmd("trash").alias("disposal", "bin").perm("anyplugin.trash").playerOnly().exec(ctx -> views.openTrash(ctx.player()));
        r.cmd("i").alias("item").perm("anyplugin.item").playerOnly().logged().exec(this::item).tab(ctx -> {
            if (ctx.index() != 0) {
                return List.of("1", "16", "64");
            }
            String prefix = ctx.opt(0, "").toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Material material : Material.values()) {
                if (material.isItem() && !material.isLegacy() && material.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(material.name().toLowerCase(Locale.ROOT));
                    if (out.size() > 60) {
                        break;
                    }
                }
            }
            return out;
        });
        r.cmd("more").perm("anyplugin.more").playerOnly().exec(ctx -> {
            ItemStack item = ctx.player().getInventory().getItemInMainHand();
            if (item.isEmpty()) {
                throw new CmdException("utility.hand-empty");
            }
            item.setAmount(item.getMaxStackSize());
            ctx.send("utility.more", p("amount", item.getMaxStackSize()));
        });
        r.cmd("skull").alias("playerhead").perm("anyplugin.skull").playerOnly().exec(this::skull).tab(ctx -> playersFirst(ctx, 0));
        r.cmd("day").perm("anyplugin.time").exec(ctx -> time(ctx, 1000, "utility.time-day"));
        r.cmd("night").perm("anyplugin.time").exec(ctx -> time(ctx, 13000, "utility.time-night"));
        r.cmd("sun").alias("clear").perm("anyplugin.weather").exec(ctx -> weather(ctx, false, false, "utility.weather-clear"));
        r.cmd("rain").perm("anyplugin.weather").exec(ctx -> weather(ctx, true, false, "utility.weather-rain"));
        r.cmd("thunder").alias("storm").perm("anyplugin.weather").exec(ctx -> weather(ctx, true, true, "utility.weather-thunder"));
        r.cmd("near").perm("anyplugin.near").playerOnly().exec(this::near);
        r.cmd("ping").perm("anyplugin.ping").exec(ctx -> {
            Player target = ctx.present(0) ? ctx.online(0) : ctx.player();
            ctx.send(target == ctx.sender ? "utility.ping-self" : "utility.ping-other", p("player", target.getName()), p("ping", target.getPing()));
        }).tab(ctx -> playersFirst(ctx, 0));
        r.cmd("list").alias("online", "who", "playerlist").perm("anyplugin.list").exec(this::list);
        r.cmd("seen").perm("anyplugin.seen").exec(this::seen).tab(ctx -> playersFirst(ctx, 0));
        r.cmd("whois").perm("anyplugin.whois").exec(this::whois).tab(ctx -> playersFirst(ctx, 0));
        r.cmd("playtime").alias("pt").perm("anyplugin.playtime").exec(this::playtime).tab(ctx -> {
            if (ctx.index() != 0) {
                return List.of();
            }
            List<String> out = new ArrayList<>(CommandRegistry.players(ctx.sender));
            out.add("top");
            return out;
        });
        r.cmd("nick").alias("nickname").perm("anyplugin.nick").exec(this::nick).tab(ctx -> ctx.index() == 0 ? List.of("off") : playersFirst(ctx, 1));
        r.cmd("realname").perm("anyplugin.realname").exec(ctx -> {
            String query = ctx.arg(0);
            List<String> found = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData data = plugin.players().data(player);
                if (data.nick != null && Text.stripTags(data.nick).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                        && plugin.players().canSee(ctx.sender, player)) {
                    found.add(Text.stripTags(data.nick) + " -> " + player.getName());
                }
            }
            if (found.isEmpty()) {
                throw new CmdException("utility.realname-none", p("nick", query));
            }
            ctx.send("utility.realname", p("list", String.join(", ", found)));
        });
    }

    private void gameModes(CommandRegistry r) {
        r.cmd("gm").alias("gamemode").perm("anyplugin.gamemode").logged().exec(ctx -> {
            GameMode mode = parseMode(ctx.arg(0));
            if (mode == null) {
                throw ctx.usage();
            }
            setMode(ctx, mode, 1);
        }).tab(ctx -> ctx.index() == 0 ? List.of("survival", "creative", "adventure", "spectator", "0", "1", "2", "3") : playersFirst(ctx, 1));
        Map<String, GameMode> shortcuts = new LinkedHashMap<>();
        shortcuts.put("gms", GameMode.SURVIVAL);
        shortcuts.put("gmc", GameMode.CREATIVE);
        shortcuts.put("gma", GameMode.ADVENTURE);
        shortcuts.put("gmsp", GameMode.SPECTATOR);
        shortcuts.forEach((label, mode) -> r.cmd(label).perm("anyplugin.gamemode").logged()
                .exec(ctx -> setMode(ctx, mode, 0)).tab(ctx -> playersFirst(ctx, 0)));
    }

    private static GameMode parseMode(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival", "в", "выживание" -> GameMode.SURVIVAL;
            case "1", "c", "creative", "к", "творчество" -> GameMode.CREATIVE;
            case "2", "a", "adventure", "п", "приключение" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator", "н", "наблюдатель" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private void setMode(Ctx ctx, GameMode mode, int targetIndex) {
        String modeKey = mode.name().toLowerCase(Locale.ROOT);
        ctx.require("anyplugin.gamemode." + modeKey);
        Player target = ctx.targetOrSelf(targetIndex, "anyplugin.gamemode.others");
        String modeName = plugin.msg().raw("gamemode." + modeKey);
        plugin.tasks().entity(target, () -> target.setGameMode(mode));
        if (target == ctx.sender) {
            ctx.send("utility.gamemode-self", p("mode", modeName));
        } else {
            ctx.send("utility.gamemode-other", p("mode", modeName), p("player", target.getName()));
            plugin.msg().send(target, "utility.gamemode-self", p("mode", modeName));
        }
    }

    private void fly(Ctx ctx) {
        Player target = ctx.targetOrSelf(0, "anyplugin.fly.others");
        PlayerData data = plugin.players().data(target);
        boolean enable = !target.getAllowFlight();
        data.fly = enable;
        plugin.players().setFlag(data, "fly", enable);
        plugin.tasks().entity(target, () -> {
            target.setAllowFlight(enable);
            if (!enable) {
                target.setFlying(false);
            }
        });
        String key = enable ? "utility.fly-on" : "utility.fly-off";
        plugin.msg().send(target, key);
        if (target != ctx.sender) {
            ctx.send(key + "-other", p("player", target.getName()));
        }
    }

    private void speed(Ctx ctx) {
        int value = ctx.integer(0, 0, 10);
        Player target = ctx.present(2) ? ctx.online(2) : ctx.player();
        if (target != ctx.sender) {
            ctx.require("anyplugin.speed.others");
        }
        boolean flying = ctx.present(1) ? ctx.arg(1).toLowerCase(Locale.ROOT).startsWith("f") : target.isFlying();
        float speed = flying ? (value == 0 ? 0f : 0.1f + (value - 1) * (0.9f / 9f)) : (value == 0 ? 0f : 0.2f + (value - 1) * (0.8f / 9f));
        float clamped = Math.min(1f, speed);
        plugin.tasks().entity(target, () -> {
            if (flying) {
                target.setFlySpeed(clamped);
            } else {
                target.setWalkSpeed(clamped);
            }
        });
        ctx.send(flying ? "utility.speed-fly" : "utility.speed-walk", p("value", value), p("player", target.getName()));
    }

    private void god(Ctx ctx) {
        Player target = ctx.targetOrSelf(0, "anyplugin.god.others");
        PlayerData data = plugin.players().data(target);
        data.god = !data.god;
        plugin.players().setFlag(data, "god", data.god);
        if (data.god) {
            plugin.tasks().entity(target, () -> {
                target.setFireTicks(0);
                target.setFoodLevel(20);
            });
        }
        String key = data.god ? "utility.god-on" : "utility.god-off";
        plugin.msg().send(target, key);
        if (target != ctx.sender) {
            ctx.send(key + "-other", p("player", target.getName()));
        }
    }

    private void heal(Ctx ctx) {
        Player target = ctx.targetOrSelf(0, "anyplugin.heal.others");
        plugin.tasks().entity(target, () -> {
            AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
            target.setHealth(max == null ? 20.0 : max.getValue());
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.setFireTicks(0);
            target.setFreezeTicks(0);
            target.getActivePotionEffects().stream()
                    .filter(effect -> !effect.getType().isInstant() && effect.getType().getEffectCategory() == org.bukkit.potion.PotionEffectType.Category.HARMFUL)
                    .forEach(effect -> target.removePotionEffect(effect.getType()));
        });
        plugin.msg().send(target, "utility.healed");
        if (target != ctx.sender) {
            ctx.send("utility.healed-other", p("player", target.getName()));
        }
    }

    private void feed(Ctx ctx) {
        Player target = ctx.targetOrSelf(0, "anyplugin.feed.others");
        plugin.tasks().entity(target, () -> {
            target.setFoodLevel(20);
            target.setSaturation(20f);
        });
        plugin.msg().send(target, "utility.fed");
        if (target != ctx.sender) {
            ctx.send("utility.fed-other", p("player", target.getName()));
        }
    }

    private void repair(Ctx ctx) {
        Player player = ctx.player();
        if (ctx.present(0) && ctx.arg(0).equalsIgnoreCase("all")) {
            ctx.require("anyplugin.repair.all");
            int count = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (repairItem(item)) {
                    count++;
                }
            }
            ctx.send("utility.repaired-all", p("count", count));
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!repairItem(item)) {
            throw new CmdException("utility.repair-nothing");
        }
        ctx.send("utility.repaired");
    }

    private static boolean repairItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable && damageable.hasDamage() && damageable.getDamage() > 0) {
            damageable.setDamage(0);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }

    private void hat(Ctx ctx) {
        Player player = ctx.player();
        PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        if (hand.isEmpty()) {
            throw new CmdException("utility.hand-empty");
        }
        ItemStack helmet = inventory.getHelmet();
        ItemStack one = hand.clone();
        one.setAmount(1);
        inventory.setHelmet(one);
        hand.setAmount(hand.getAmount() - 1);
        inventory.setItem(EquipmentSlot.HAND, hand.getAmount() <= 0 ? null : hand);
        if (helmet != null && !helmet.isEmpty()) {
            inventory.addItem(helmet).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        ctx.send("utility.hat");
    }

    private void workbench(CommandRegistry r, String label, String perm, MenuType.Typed<?, ?> type, String... aliases) {
        r.cmd(label).alias(aliases).perm(perm).playerOnly().exec(ctx -> {
            Player player = ctx.player();
            Object builder = type.builder();
            org.bukkit.inventory.InventoryView view;
            if (builder instanceof org.bukkit.inventory.view.builder.LocationInventoryViewBuilder<?> location) {
                view = location.checkReachable(false).build(player);
            } else {
                view = type.create(player);
            }
            player.openInventory(view);
        });
    }

    private void enderChest(Ctx ctx) {
        Player player = ctx.player();
        if (!ctx.present(0) || ctx.arg(0).equalsIgnoreCase(player.getName())) {
            player.openInventory(player.getEnderChest());
            return;
        }
        ctx.require("anyplugin.enderchest.others");
        Player target = ctx.online(0);
        views.openOther(player, target, target.getEnderChest(), plugin.msg().get("invsee.enderchest-title", p("player", target.getName())),
                ctx.has("anyplugin.enderchest.others.edit"));
    }

    private void item(Ctx ctx) {
        Player player = ctx.player();
        Material material = Material.matchMaterial(ctx.arg(0));
        if (material == null || !material.isItem() || material.isAir()) {
            throw new CmdException("utility.item-unknown", p("item", ctx.arg(0)));
        }
        int amount = ctx.present(1) ? ctx.integer(1, 1, material.getMaxStackSize() * 36) : material.getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int stack = Math.min(left, material.getMaxStackSize());
            player.getInventory().addItem(new ItemStack(material, stack)).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            left -= stack;
        }
        ctx.send("utility.item-given", p("amount", amount), p("item", material.getKey().getKey()));
    }

    private void skull(Ctx ctx) {
        Player player = ctx.player();
        String owner = ctx.opt(0, player.getName());
        if (!owner.matches("[A-Za-z0-9_]{1,16}")) {
            throw new CmdException("utility.skull-bad-name");
        }
        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(owner);
        profile.update().whenComplete((completed, error) -> plugin.tasks().entity(player, () -> {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setPlayerProfile(error == null && completed != null ? completed : profile);
            head.setItemMeta(meta);
            player.getInventory().addItem(head).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            plugin.msg().send(player, "utility.skull", p("player", owner));
        }));
    }

    private World worldArg(Ctx ctx, int index) {
        if (ctx.present(index)) {
            World world = Bukkit.getWorld(ctx.arg(index));
            if (world == null) {
                throw new CmdException("errors.world-not-found", p("world", ctx.arg(index)));
            }
            return world;
        }
        if (ctx.sender instanceof Player player) {
            return player.getWorld();
        }
        return Bukkit.getWorlds().get(0);
    }

    private void time(Ctx ctx, long ticks, String key) {
        World world = worldArg(ctx, 0);
        plugin.tasks().global(() -> world.setTime(ticks));
        ctx.send(key, p("world", world.getName()));
    }

    private void weather(Ctx ctx, boolean storm, boolean thunder, String key) {
        World world = worldArg(ctx, 0);
        plugin.tasks().global(() -> {
            world.setStorm(storm);
            world.setThundering(thunder);
            if (!storm) {
                world.setClearWeatherDuration(20 * 60 * 15);
            }
        });
        ctx.send(key, p("world", world.getName()));
    }

    private void near(Ctx ctx) {
        Player player = ctx.player();
        int max = plugin.getConfig().getInt("utility.near-max-radius", 500);
        int radius = ctx.present(0) ? ctx.integer(0, 1, max) : plugin.getConfig().getInt("utility.near-default-radius", 200);
        Location here = player.getLocation();
        List<String> found = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || !other.getWorld().equals(here.getWorld()) || !plugin.players().canSee(player, other)) {
                continue;
            }
            double distance = other.getLocation().distance(here);
            if (distance <= radius) {
                found.add(other.getName() + " (" + (int) distance + ")");
            }
        }
        if (found.isEmpty()) {
            ctx.send("utility.near-none", p("radius", radius));
        } else {
            ctx.send("utility.near", p("radius", radius), p("players", String.join(", ", found)));
        }
    }

    private void list(Ctx ctx) {
        Map<String, Group> groups = new LinkedHashMap<>();
        Map<String, List<Player>> grouped = new LinkedHashMap<>();
        int visible = 0;
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (Player player : players) {
            if (!plugin.players().canSee(ctx.sender, player)) {
                continue;
            }
            visible++;
            Group group = plugin.perms().primary(player);
            groups.putIfAbsent(group.name, group);
            grouped.computeIfAbsent(group.name, g -> new ArrayList<>()).add(player);
        }
        ctx.send("list.header", p("online", visible), p("max", Bukkit.getMaxPlayers()));
        List<Group> order = new ArrayList<>(groups.values());
        order.sort(Comparator.comparingInt((Group g) -> g.weight).reversed());
        for (Group group : order) {
            List<Component> names = new ArrayList<>();
            for (Player player : grouped.get(group.name)) {
                PlayerData data = plugin.players().data(player);
                Component name = player.displayName();
                if (data.afk) {
                    name = name.append(plugin.msg().get("tab.afk-tag"));
                }
                if (data.vanished) {
                    name = name.append(plugin.msg().get("tab.vanish-tag"));
                }
                names.add(name);
            }
            ctx.send("list.group", p("group", group.name), mm("prefix", group.prefix), p("count", names.size()),
                    c("players", Component.join(JoinConfiguration.separator(Component.text(", ")), names)));
        }
    }

    private void seen(Ctx ctx) {
        String name = ctx.arg(0);
        Player online = plugin.players().findOnline(ctx.sender, name);
        if (online != null) {
            PlayerData data = plugin.players().data(online);
            ctx.send("seen.online", p("player", online.getName()), p("session", plugin.msg().duration(System.currentTimeMillis() - data.sessionStart)));
            return;
        }
        plugin.players().lookup(name).thenAccept(found -> {
            if (found.isEmpty()) {
                ctx.send("errors.player-never-joined", p("player", name));
                return;
            }
            OfflineRecord record = found.get();
            ctx.send("seen.offline", p("player", record.name()), p("date", Text.date(record.lastSeen())),
                    p("ago", plugin.msg().duration(System.currentTimeMillis() - record.lastSeen())));
        });
    }

    private void whois(Ctx ctx) {
        String name = ctx.arg(0);
        boolean showIp = ctx.has("anyplugin.whois.ip");
        Player online = plugin.players().findOnline(ctx.sender, name);
        if (online != null) {
            PlayerData data = plugin.players().data(online);
            Location l = online.getLocation();
            ctx.send("whois.header", p("player", online.getName()));
            ctx.send("whois.uuid", p("uuid", online.getUniqueId()));
            ctx.send("whois.nick", mm("nick", data.nick == null ? "-" : data.nick));
            ctx.send("whois.group", p("group", plugin.perms().primary(online).name));
            ctx.send("whois.location", p("location", Locations.brief(l)));
            ctx.send("whois.state", p("mode", plugin.msg().raw("gamemode." + online.getGameMode().name().toLowerCase(Locale.ROOT))),
                    p("health", (int) Math.ceil(online.getHealth())), p("food", online.getFoodLevel()), p("fly", yesNo(online.getAllowFlight())),
                    p("god", yesNo(data.god)), p("afk", yesNo(data.afk)), p("vanish", yesNo(data.vanished)));
            ctx.send("whois.times", p("first", Text.date(data.firstJoin)), p("playtime", plugin.msg().duration(data.totalPlaytime())));
            if (showIp) {
                ctx.send("whois.ip", p("ip", data.lastIp == null ? "-" : data.lastIp));
            }
            if (data.muted()) {
                ctx.send("whois.muted", p("reason", data.mute.reason()));
            }
            return;
        }
        plugin.players().lookup(name).thenAccept(found -> {
            if (found.isEmpty()) {
                ctx.send("errors.player-never-joined", p("player", name));
                return;
            }
            OfflineRecord record = found.get();
            ctx.send("whois.header", p("player", record.name()));
            ctx.send("whois.uuid", p("uuid", record.uuid()));
            ctx.send("whois.nick", mm("nick", record.nick() == null ? "-" : record.nick()));
            ctx.send("whois.offline", p("date", Text.date(record.lastSeen())));
            ctx.send("whois.times", p("first", Text.date(record.firstJoin())), p("playtime", plugin.msg().duration(record.playtime())));
            if (showIp) {
                ctx.send("whois.ip", p("ip", record.lastIp() == null ? "-" : record.lastIp()));
            }
        });
    }

    private String yesNo(boolean value) {
        return plugin.msg().raw(value ? "common.yes" : "common.no");
    }

    private void playtime(Ctx ctx) {
        if (ctx.present(0) && ctx.arg(0).equalsIgnoreCase("top")) {
            plugin.players().topPlaytime(10).thenAccept(list -> {
                ctx.send("playtime.top-header");
                int i = 1;
                for (OfflineRecord record : list) {
                    PlayerData live = plugin.players().data(record.uuid());
                    long time = live != null ? live.totalPlaytime() : record.playtime();
                    ctx.send("playtime.top-line", p("place", i++), p("player", record.name()), p("time", plugin.msg().duration(time)));
                }
            });
            return;
        }
        if (!ctx.present(0)) {
            Player player = ctx.player();
            ctx.send("playtime.self", p("time", plugin.msg().duration(plugin.players().data(player).totalPlaytime())));
            return;
        }
        ctx.require("anyplugin.playtime.others");
        String name = ctx.arg(0);
        Player online = plugin.players().findOnline(ctx.sender, name);
        if (online != null) {
            ctx.send("playtime.other", p("player", online.getName()), p("time", plugin.msg().duration(plugin.players().data(online).totalPlaytime())));
            return;
        }
        plugin.players().lookup(name).thenAccept(found -> {
            if (found.isEmpty()) {
                ctx.send("errors.player-never-joined", p("player", name));
            } else {
                ctx.send("playtime.other", p("player", found.get().name()), p("time", plugin.msg().duration(found.get().playtime())));
            }
        });
    }

    private void nick(Ctx ctx) {
        String raw = ctx.arg(0);
        Player target = ctx.present(1) ? ctx.online(1) : ctx.player();
        if (target != ctx.sender) {
            ctx.require("anyplugin.nick.others");
        }
        PlayerData data = plugin.players().data(target);
        if (raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("reset")) {
            data.nick = null;
            plugin.players().setText(target.getUniqueId(), "nick", null);
            applyNick(target, data);
            ctx.send("nick.reset", p("player", target.getName()));
            return;
        }
        String value = ctx.has("anyplugin.nick.color") ? Text.sanitize(raw) : Text.escape(Text.plain(Text.userInput(raw, false)));
        String plain = Text.plain(Text.userInput(value, true));
        int maxLength = plugin.getConfig().getInt("utility.nick-max-length", 16);
        if (plain.length() < 2 || plain.length() > maxLength || !plain.matches("[\\p{L}0-9_\\-.]+")) {
            throw new CmdException("nick.invalid", p("max", maxLength));
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(target)) {
                continue;
            }
            PlayerData otherData = plugin.players().data(other.getUniqueId());
            boolean nickTaken = otherData != null && otherData.nick != null && Text.stripTags(otherData.nick).equalsIgnoreCase(plain);
            if (other.getName().equalsIgnoreCase(plain) || nickTaken) {
                throw new CmdException("nick.taken", p("nick", plain));
            }
        }
        data.nick = value;
        plugin.players().setText(target.getUniqueId(), "nick", value);
        applyNick(target, data);
        ctx.send("nick.set", p("player", target.getName()), mm("nick", value));
    }

    private void applyNick(Player target, PlayerData data) {
        plugin.tasks().entity(target, () -> {
            plugin.players().applyDisplayName(target, data);
            if (plugin.tab() != null) {
                plugin.tab().refresh(target);
            }
        });
    }
}
