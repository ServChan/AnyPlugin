package org.lts.anyplugin.server;

import java.util.List;
import java.util.Locale;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.core.Tasks;
import org.lts.anyplugin.core.Text;

import static org.lts.anyplugin.core.Messages.p;

public final class CoreCommands {

    private record LogEntry(long time, String actor, String action) {
    }

    private final AnyPlugin plugin;

    public CoreCommands(AnyPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(CommandRegistry registry) {
        registry.cmd("anyplugin").alias("ap").perm("anyplugin.manage").exec(this::run)
                .tab(ctx -> ctx.index() == 0 ? List.of("reload", "modules", "version", "log") : List.of());
    }

    private void run(Ctx ctx) {
        String action = ctx.opt(0, "version").toLowerCase(Locale.ROOT);
        switch (action) {
            case "reload" -> {
                plugin.reloadAll();
                ctx.send("core.reloaded");
            }
            case "modules" -> {
                ctx.send("core.modules-header");
                for (String module : AnyPlugin.MODULES) {
                    boolean on = plugin.module(module);
                    ctx.send(on ? "core.module-on" : "core.module-off", p("module", module));
                }
                if (!plugin.perms().internal()) {
                    ctx.send("core.luckperms-detected");
                }
            }
            case "log" -> {
                int page = ctx.present(1) ? ctx.integer(1, 1, 1000) : 1;
                plugin.db().list("SELECT * FROM staff_log ORDER BY id DESC LIMIT 15 OFFSET ?", rs -> new LogEntry(rs.getLong("time"),
                        rs.getString("actor"), rs.getString("action")), (page - 1) * 15).thenAccept(list -> {
                            ctx.send("core.log-header", p("page", page));
                            for (LogEntry entry : list) {
                                ctx.send("core.log-line", p("date", Text.date(entry.time())), p("actor", entry.actor()), p("action", entry.action()));
                            }
                        });
            }
            default -> ctx.send("core.version", p("version", plugin.getPluginMeta().getVersion()), p("platform", Tasks.FOLIA ? "Folia" : "Paper"));
        }
    }
}
