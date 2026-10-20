package org.lts.anyplugin.command;

import java.util.Arrays;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.core.Durations;
import org.lts.anyplugin.core.Messages;

public final class Ctx {

    public final AnyPlugin plugin;
    public final Cmd cmd;
    public final CommandSender sender;
    public final String[] args;

    Ctx(AnyPlugin plugin, Cmd cmd, CommandSender sender, String[] args) {
        this.plugin = plugin;
        this.cmd = cmd;
        this.sender = sender;
        this.args = args;
    }

    public int size() {
        return args.length;
    }

    public int index() {
        return Math.max(0, args.length - 1);
    }

    public String arg(int i) {
        if (i >= args.length) {
            throw usage();
        }
        return args[i];
    }

    public String opt(int i, String fallback) {
        return i < args.length ? args[i] : fallback;
    }

    public boolean present(int i) {
        return i < args.length;
    }

    public String joined(int from) {
        String text = joinedOpt(from);
        if (text.isEmpty()) {
            throw usage();
        }
        return text;
    }

    public String joinedOpt(int from) {
        if (from >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    public boolean isPlayer() {
        return sender instanceof Player;
    }

    public Player player() {
        if (sender instanceof Player player) {
            return player;
        }
        throw new CmdException("errors.only-players");
    }

    public boolean has(String permission) {
        return sender.hasPermission(permission);
    }

    public void require(String permission) {
        if (!sender.hasPermission(permission)) {
            throw new CmdException("errors.no-permission");
        }
    }

    public Player online(int i) {
        String name = arg(i);
        Player player = plugin.players().findOnline(sender, name);
        if (player == null) {
            throw new CmdException("errors.player-not-found", Messages.p("player", name));
        }
        return player;
    }

    public Player targetOrSelf(int i, String othersPermission) {
        if (present(i)) {
            Player target = online(i);
            if (sender != target) {
                require(othersPermission);
            }
            return target;
        }
        return player();
    }

    public int integer(int i, int min, int max) {
        String raw = arg(i);
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                throw new CmdException("errors.number-range", Messages.p("min", min), Messages.p("max", max));
            }
            return value;
        } catch (NumberFormatException e) {
            throw new CmdException("errors.number", Messages.p("value", raw));
        }
    }

    public double decimal(int i) {
        String raw = arg(i);
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new CmdException("errors.number", Messages.p("value", raw));
        }
    }

    public long duration(int i) {
        String raw = arg(i);
        long millis = Durations.parse(raw);
        if (millis <= 0) {
            throw new CmdException("errors.duration", Messages.p("value", raw));
        }
        return millis;
    }

    public void send(String key, TagResolver... resolvers) {
        plugin.msg().send(sender, key, resolvers);
    }

    public CmdException usage() {
        return new CmdException("usage." + cmd.name());
    }

    public CmdException fail(String key, TagResolver... resolvers) {
        return new CmdException(key, resolvers);
    }
}
