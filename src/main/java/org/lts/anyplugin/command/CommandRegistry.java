package org.lts.anyplugin.command;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.core.Text;

public final class CommandRegistry {

    private final AnyPlugin plugin;
    private final List<Cmd> commands = new ArrayList<>();

    public CommandRegistry(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public Cmd cmd(String name) {
        Cmd cmd = new Cmd(plugin, name);
        commands.add(cmd);
        return cmd;
    }

    public List<Cmd> all() {
        return Collections.unmodifiableList(commands);
    }

    public void install() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            for (Cmd cmd : commands) {
                String key = "desc." + cmd.name();
                String description = plugin.msg().has(key) ? Text.stripTags(plugin.msg().raw(key)) : null;
                registrar.register(cmd.name(), description, cmd.aliases(), cmd);
            }
        });
    }

    public static List<String> players(CommandSender viewer) {
        List<String> names = new ArrayList<>();
        AnyPlugin plugin = AnyPlugin.get();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.players().canSee(viewer, player)) {
                names.add(player.getName());
            }
        }
        return names;
    }

    public static List<String> of(String... options) {
        return List.of(options);
    }

    public static List<String> lower(List<String> options) {
        List<String> out = new ArrayList<>(options.size());
        for (String option : options) {
            out.add(option.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
