package org.lts.anyplugin.chat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.core.Text;
import org.lts.anyplugin.perms.Group;
import org.lts.anyplugin.player.PlayerData;

import static org.lts.anyplugin.core.Messages.c;
import static org.lts.anyplugin.core.Messages.mm;
import static org.lts.anyplugin.core.Messages.p;

public final class ChatService implements Listener {

    private static final Sound MENTION = Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.PLAYER, 0.8f, 1.4f);
    private static final Sound PM = Sound.sound(Key.key("minecraft:block.note_block.pling"), Sound.Source.PLAYER, 0.6f, 1.6f);

    private final AnyPlugin plugin;
    private volatile UUID consoleReplyTarget;

    public ChatService(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void register(CommandRegistry registry) {
        registry.cmd("msg").alias("tell", "w", "m", "pm", "whisper").perm("anyplugin.msg")
                .exec(ctx -> privateMessage(ctx.sender, ctx.online(0), ctx.joined(1)))
                .tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
        registry.cmd("r").alias("reply").perm("anyplugin.msg").exec(this::reply);
        registry.cmd("ignore").perm("anyplugin.ignore").playerOnly().exec(this::ignore)
                .tab(ctx -> ctx.index() == 0 ? CommandRegistry.players(ctx.sender) : List.of());
        registry.cmd("msgtoggle").perm("anyplugin.msgtoggle").playerOnly().exec(ctx -> {
            PlayerData data = plugin.players().data(ctx.player());
            data.msgToggle = !data.msgToggle;
            plugin.players().setFlag(data, "msgtoggle", data.msgToggle);
            ctx.send(data.msgToggle ? "msg.toggle-off" : "msg.toggle-on");
        });
        registry.cmd("socialspy").perm("anyplugin.socialspy").playerOnly().exec(ctx -> {
            PlayerData data = plugin.players().data(ctx.player());
            data.socialSpy = !data.socialSpy;
            plugin.players().setFlag(data, "socialspy", data.socialSpy);
            ctx.send(data.socialSpy ? "msg.spy-on" : "msg.spy-off");
        });
        registry.cmd("broadcast").alias("bc").perm("anyplugin.broadcast").logged().exec(ctx -> {
            Component line = plugin.msg().get("chat.broadcast", c("message", Text.trusted(ctx.joined(0))));
            Bukkit.getServer().sendMessage(line);
        });
        registry.cmd("clearchat").alias("cc").perm("anyplugin.clearchat").logged().exec(ctx -> {
            Component empty = Component.empty();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("anyplugin.clearchat.exempt")) {
                    for (int i = 0; i < 100; i++) {
                        player.sendMessage(empty);
                    }
                }
            }
            Bukkit.getServer().sendMessage(plugin.msg().get("chat.cleared", p("player", ctx.sender.getName())));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().data(player);
        data.lastActivity = System.currentTimeMillis();
        if (data.muted()) {
            event.setCancelled(true);
            plugin.msg().send(player, "mute.chat-blocked", p("reason", data.mute.reason()),
                    p("remaining", data.mute.expires() == null ? plugin.msg().raw("time.forever") : plugin.msg().duration(data.mute.expires() - System.currentTimeMillis())));
            return;
        }
        String text = Text.plain(event.message());
        if (plugin.staff() != null && (data.staffChat || isStaffPrefix(player, text))) {
            event.setCancelled(true);
            String body = data.staffChat ? text : text.substring(cfg().getString("staff-chat.prefix", "#").length()).trim();
            if (!body.isEmpty()) {
                plugin.staff().staffChat(player, body);
            }
            return;
        }
        if (!player.hasPermission("anyplugin.chat.bypass-spam")) {
            long now = System.currentTimeMillis();
            long cooldown = cfg().getLong("chat.cooldown-ms", 1000);
            if (now - data.lastChat < cooldown) {
                event.setCancelled(true);
                plugin.msg().send(player, "chat.too-fast");
                return;
            }
            if (cfg().getBoolean("chat.block-repeats", true) && text.equalsIgnoreCase(data.lastChatText) && now - data.lastChat < 30_000) {
                event.setCancelled(true);
                plugin.msg().send(player, "chat.repeat");
                return;
            }
            data.lastChat = now;
            data.lastChatText = text;
            text = limitCaps(text);
        }
        Component body = Text.userInput(text, player.hasPermission("anyplugin.chat.color"));
        body = Text.linkify(body);
        body = itemTag(player, body);
        body = mentions(player, body);
        Component line = render(player, body);
        event.viewers().removeIf(viewer -> viewer instanceof Player p && ignores(p, player));
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> line));
    }

    private boolean isStaffPrefix(Player player, String text) {
        String prefix = cfg().getString("staff-chat.prefix", "#");
        return !prefix.isEmpty() && text.startsWith(prefix) && text.length() > prefix.length() && player.hasPermission("anyplugin.staffchat");
    }

    private String limitCaps(String text) {
        int max = cfg().getInt("chat.max-caps-percent", 70);
        if (max >= 100 || text.length() < 8) {
            return text;
        }
        int letters = 0;
        int upper = 0;
        for (char ch : text.toCharArray()) {
            if (Character.isLetter(ch)) {
                letters++;
                if (Character.isUpperCase(ch)) {
                    upper++;
                }
            }
        }
        if (letters > 0 && upper * 100 / letters > max) {
            return text.toLowerCase(Locale.ROOT);
        }
        return text;
    }

    private Component itemTag(Player player, Component body) {
        if (!cfg().getBoolean("chat.item-tag", true) || !player.hasPermission("anyplugin.chat.item")) {
            return body;
        }
        ItemStack item = player.getInventory().getItemInMainHand().clone();
        if (item.isEmpty()) {
            return body;
        }
        Component shown = item.displayName();
        if (item.getAmount() > 1) {
            shown = shown.append(Component.text(" x" + item.getAmount()));
        }
        Component finalShown = shown;
        return body.replaceText(TextReplacementConfig.builder().matchLiteral("[item]").replacement(finalShown).build());
    }

    private Component mentions(Player sender, Component body) {
        if (!cfg().getBoolean("chat.mentions", true)) {
            return body;
        }
        String plain = Text.plain(body).toLowerCase(Locale.ROOT);
        List<Player> mentioned = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(sender) || !plugin.players().canSee(sender, player)) {
                continue;
            }
            if (plain.contains(player.getName().toLowerCase(Locale.ROOT))) {
                mentioned.add(player);
            }
        }
        if (mentioned.isEmpty()) {
            return body;
        }
        for (Player target : mentioned) {
            Pattern pattern = Pattern.compile("(?i)@?(?<![\\w])" + Pattern.quote(target.getName()) + "(?![\\w])");
            body = body.replaceText(TextReplacementConfig.builder().match(pattern)
                    .replacement(match -> plugin.msg().get("chat.mention", p("player", target.getName()))).build());
            if (!ignores(target, sender)) {
                plugin.tasks().entity(target, () -> {
                    target.playSound(MENTION);
                    if (cfg().getBoolean("chat.mention-actionbar", true)) {
                        target.sendActionBar(plugin.msg().get("chat.mention-actionbar", p("player", sender.getName())));
                    }
                });
            }
        }
        return body;
    }

    public Component render(Player player, Component body) {
        Group group = plugin.perms().primary(player);
        String format = group.chatFormat != null && !group.chatFormat.isEmpty() ? group.chatFormat : cfg().getString("chat.format",
                "<prefix><name><suffix><dark_gray> » <white><message>");
        Component name = player.displayName()
                .hoverEvent(HoverEvent.showText(plugin.msg().get("chat.name-hover", p("player", player.getName()),
                        p("group", group.name), p("world", player.getWorld().getName()))))
                .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "));
        if (!group.nameColor.isEmpty()) {
            net.kyori.adventure.text.format.NamedTextColor color = net.kyori.adventure.text.format.NamedTextColor.NAMES.value(group.nameColor.toLowerCase(Locale.ROOT));
            if (color != null) {
                name = name.colorIfAbsent(color);
            }
        }
        TagResolver resolvers = TagResolver.resolver(
                mm("prefix", plugin.perms().prefix(player)),
                mm("suffix", plugin.perms().suffix(player)),
                c("name", name),
                c("message", body),
                p("world", player.getWorld().getName()),
                p("group", group.name),
                p("player", player.getName()));
        return plugin.msg().parse(format, resolvers);
    }

    public boolean ignores(Player viewer, Player sender) {
        if (sender.hasPermission("anyplugin.ignore.exempt")) {
            return false;
        }
        PlayerData data = plugin.players().data(viewer);
        return data.ignores.contains(sender.getUniqueId());
    }

    private void ignore(Ctx ctx) {
        Player player = ctx.player();
        PlayerData data = plugin.players().data(player);
        if (!ctx.present(0)) {
            if (data.ignores.isEmpty()) {
                ctx.send("ignore.empty");
                return;
            }
            List<String> names = new ArrayList<>();
            for (UUID id : data.ignores) {
                String name = Bukkit.getOfflinePlayer(id).getName();
                names.add(name == null ? id.toString().substring(0, 8) : name);
            }
            ctx.send("ignore.list", p("players", String.join(", ", names)));
            return;
        }
        Player target = ctx.online(0);
        if (target.equals(player)) {
            throw new CmdException("ignore.self");
        }
        if (data.ignores.remove(target.getUniqueId())) {
            plugin.db().update("DELETE FROM ignores WHERE uuid=? AND ignored=?", player.getUniqueId().toString(), target.getUniqueId().toString());
            ctx.send("ignore.removed", p("player", target.getName()));
            return;
        }
        if (target.hasPermission("anyplugin.ignore.exempt")) {
            throw new CmdException("ignore.exempt", p("player", target.getName()));
        }
        data.ignores.add(target.getUniqueId());
        plugin.db().update("INSERT OR IGNORE INTO ignores (uuid, ignored) VALUES (?,?)", player.getUniqueId().toString(), target.getUniqueId().toString());
        ctx.send("ignore.added", p("player", target.getName()));
    }

    private void reply(Ctx ctx) {
        UUID target = ctx.sender instanceof Player player ? plugin.players().data(player).lastMessaged : consoleReplyTarget;
        if (target == null) {
            throw new CmdException("msg.no-reply");
        }
        Player recipient = Bukkit.getPlayer(target);
        if (recipient == null || !plugin.players().canSee(ctx.sender, recipient)) {
            throw new CmdException("msg.reply-offline");
        }
        privateMessage(ctx.sender, recipient, ctx.joined(0));
    }

    public void privateMessage(CommandSender sender, Player target, String text) {
        if (sender instanceof Player player) {
            if (player.equals(target)) {
                throw new CmdException("msg.self");
            }
            PlayerData data = plugin.players().data(player);
            if (data.muted() && cfg().getBoolean("chat.mute-blocks-pm", true)) {
                throw new CmdException("mute.chat-blocked", p("reason", data.mute.reason()),
                        p("remaining", data.mute.expires() == null ? plugin.msg().raw("time.forever") : plugin.msg().duration(data.mute.expires() - System.currentTimeMillis())));
            }
            PlayerData targetData = plugin.players().data(target);
            if (targetData.msgToggle && !player.hasPermission("anyplugin.msgtoggle.bypass")) {
                throw new CmdException("msg.target-disabled", p("player", target.getName()));
            }
            if (ignores(target, player)) {
                throw new CmdException("msg.target-ignores", p("player", target.getName()));
            }
            data.lastActivity = System.currentTimeMillis();
            data.lastMessaged = target.getUniqueId();
            targetData.lastMessaged = player.getUniqueId();
        } else {
            consoleReplyTarget = target.getUniqueId();
            plugin.players().data(target).lastMessaged = null;
        }
        boolean colors = sender.hasPermission("anyplugin.chat.color");
        Component body = Text.linkify(Text.userInput(text, colors));
        Component senderName = sender instanceof Player player ? player.displayName() : plugin.msg().get("msg.console-name");
        TagResolver resolvers = TagResolver.resolver(c("sender", senderName), c("target", target.displayName()), c("message", body),
                p("sender_name", sender.getName()), p("target_name", target.getName()));
        plugin.msg().send(sender, "msg.to", resolvers);
        target.sendMessage(plugin.msg().get("msg.from", resolvers)
                .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " ")));
        plugin.tasks().entity(target, () -> target.playSound(PM));
        Component spy = plugin.msg().get("msg.spy", resolvers);
        for (PlayerData data : plugin.players().all()) {
            if (!data.socialSpy || data.uuid.equals(target.getUniqueId()) || (sender instanceof Player s && data.uuid.equals(s.getUniqueId()))) {
                continue;
            }
            Player spyPlayer = Bukkit.getPlayer(data.uuid);
            if (spyPlayer != null && spyPlayer.hasPermission("anyplugin.socialspy")) {
                spyPlayer.sendMessage(spy);
            }
        }
        if (cfg().getBoolean("chat.log-private-messages", false)) {
            plugin.getLogger().info("[PM] " + sender.getName() + " -> " + target.getName() + ": " + text);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().data(player);
        if (data.vanished) {
            event.joinMessage(null);
            return;
        }
        if (data.isNew) {
            event.joinMessage(plugin.msg().get("join.first", c("player", player.displayName()), p("count", plugin.players().uniquePlayers())));
        } else {
            event.joinMessage(plugin.msg().get("join.message", c("player", player.displayName())));
        }
        plugin.tasks().entityLater(player, 10, () -> {
            for (Component line : plugin.msg().list("motd", p("player", player.getName()), p("online", plugin.players().visibleCount()))) {
                player.sendMessage(line);
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.players().data(player);
        event.quitMessage(data.vanished ? null : plugin.msg().get("quit.message", c("player", player.displayName())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        if (cfg().getBoolean("chat.death-coordinates", true)) {
            plugin.msg().send(player, "death.coordinates", p("world", location.getWorld().getName()), p("x", location.getBlockX()),
                    p("y", location.getBlockY()), p("z", location.getBlockZ()));
        }
    }

    public static void broadcastPermission(String permission, Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                player.sendMessage(message);
            }
        }
        Audience console = Bukkit.getConsoleSender();
        console.sendMessage(message);
    }
}
