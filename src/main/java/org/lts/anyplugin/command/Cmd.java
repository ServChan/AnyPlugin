package org.lts.anyplugin.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.core.Messages;

public final class Cmd implements BasicCommand {

    @FunctionalInterface
    public interface Handler {
        void run(Ctx ctx);
    }

    @FunctionalInterface
    public interface Completer {
        List<String> complete(Ctx ctx);
    }

    private final AnyPlugin plugin;
    private final String name;
    private final List<String> aliases = new ArrayList<>();
    private String permission;
    private boolean playerOnly;
    private boolean logged;
    private Handler handler = ctx -> {
    };
    private Completer completer;

    Cmd(AnyPlugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    public Cmd perm(String permission) {
        this.permission = permission;
        return this;
    }

    public Cmd alias(String... names) {
        aliases.addAll(List.of(names));
        return this;
    }

    public Cmd playerOnly() {
        this.playerOnly = true;
        return this;
    }

    public Cmd logged() {
        this.logged = true;
        return this;
    }

    public Cmd exec(Handler handler) {
        this.handler = handler;
        return this;
    }

    public Cmd tab(Completer completer) {
        this.completer = completer;
        return this;
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    @Override
    public String permission() {
        return permission;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return permission == null || sender.hasPermission(permission);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        Ctx ctx = new Ctx(plugin, this, sender, args);
        try {
            if (playerOnly && !(sender instanceof Player)) {
                throw new CmdException("errors.only-players");
            }
            handler.run(ctx);
            if (logged && plugin.staff() != null) {
                plugin.staff().log(sender, "/" + name + (args.length == 0 ? "" : " " + String.join(" ", args)));
            }
        } catch (CmdException e) {
            if (e.key().startsWith("usage.") && !plugin.msg().has(e.key())) {
                plugin.msg().send(sender, "errors.usage", Messages.p("command", "/" + name));
            } else {
                plugin.msg().send(sender, e.key(), e.resolvers());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Command /" + name + " failed", e);
            plugin.msg().send(sender, "errors.internal");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (completer == null) {
            return List.of();
        }
        String[] safeArgs = args.length == 0 ? new String[] {""} : args;
        Ctx ctx = new Ctx(plugin, this, source.getSender(), safeArgs);
        try {
            List<String> all = completer.complete(ctx);
            if (all == null) {
                return List.of();
            }
            String last = safeArgs[safeArgs.length - 1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String option : all) {
                if (option.toLowerCase(Locale.ROOT).startsWith(last)) {
                    out.add(option);
                }
            }
            return out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
