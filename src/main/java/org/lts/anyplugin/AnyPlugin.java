package org.lts.anyplugin;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.lts.anyplugin.admin.AdminChannel;
import org.lts.anyplugin.chat.ChatService;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.core.Messages;
import org.lts.anyplugin.core.Tasks;
import org.lts.anyplugin.hooks.Papi;
import org.lts.anyplugin.hooks.VaultHook;
import org.lts.anyplugin.moderation.ModerationCommands;
import org.lts.anyplugin.moderation.PunishmentService;
import org.lts.anyplugin.moderation.StaffService;
import org.lts.anyplugin.moderation.VanishService;
import org.lts.anyplugin.perms.PermCommands;
import org.lts.anyplugin.perms.PermissionService;
import org.lts.anyplugin.player.PlayerService;
import org.lts.anyplugin.server.CoreCommands;
import org.lts.anyplugin.server.InfoCommands;
import org.lts.anyplugin.server.MaintenanceService;
import org.lts.anyplugin.server.RestartService;
import org.lts.anyplugin.server.StarterKit;
import org.lts.anyplugin.server.WorldRules;
import org.lts.anyplugin.storage.Database;
import org.lts.anyplugin.tab.TabService;
import org.lts.anyplugin.teleport.HomeCommands;
import org.lts.anyplugin.teleport.RtpService;
import org.lts.anyplugin.teleport.TeleportService;
import org.lts.anyplugin.teleport.TpaService;
import org.lts.anyplugin.teleport.WarpService;
import org.lts.anyplugin.util.AfkService;
import org.lts.anyplugin.util.UtilityCommands;
import org.lts.anyplugin.util.ViewGuard;

public final class AnyPlugin extends JavaPlugin {

    public static final List<String> MODULES = List.of("permissions", "chat", "teleport", "tab", "utility", "moderation", "world",
            "maintenance", "restart", "starter-kit", "info", "admin");

    private static AnyPlugin instance;

    private Messages messages;
    private Tasks tasks;
    private Database database;
    private PlayerService players;
    private PermissionService perms;
    private CommandRegistry commands;
    private ChatService chat;
    private TeleportService teleports;
    private TabService tab;
    private PunishmentService punishments;
    private StaffService staff;
    private MaintenanceService maintenance;
    private RestartService restart;
    private AdminChannel admin;

    public static AnyPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        messages = new Messages(this);
        messages.load(getConfig().getString("language", "ru"));
        tasks = new Tasks(this);
        database = new Database(getLogger());
        try {
            database.open(new File(getDataFolder(), "data.db"));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not open data.db, disabling AnyPlugin", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        players = new PlayerService(this);
        listen(players);
        players.start();

        boolean luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        boolean internalPerms = module("permissions") && !luckPerms;
        if (module("permissions") && luckPerms) {
            getLogger().info("LuckPerms found: permission management is left to LuckPerms");
        }
        perms = new PermissionService(this, internalPerms);
        listen(perms);
        perms.start();

        commands = new CommandRegistry(this);
        WarpService warps = null;
        new CoreCommands(this).register(commands);
        if (internalPerms) {
            new PermCommands(this, perms).register(commands);
            VaultHook.register(this);
        }
        ViewGuard views = new ViewGuard(this);
        listen(views);

        if (module("chat")) {
            chat = new ChatService(this);
            listen(chat);
            chat.register(commands);
        }
        if (module("teleport")) {
            teleports = new TeleportService(this);
            listen(teleports);
            teleports.start();
            teleports.register(commands);
            new HomeCommands(this, teleports).register(commands);
            warps = new WarpService(this, teleports);
            listen(warps);
            warps.start();
            warps.register(commands);
            TpaService tpa = new TpaService(this, teleports);
            listen(tpa);
            tpa.register(commands);
            new RtpService(this, teleports).register(commands);
        }
        if (module("tab")) {
            tab = new TabService(this);
            listen(tab);
            tab.start();
        }
        if (module("utility")) {
            new UtilityCommands(this, views).register(commands);
            AfkService afk = new AfkService(this);
            listen(afk);
            afk.start();
            afk.register(commands);
        }
        if (module("moderation")) {
            punishments = new PunishmentService(this);
            new ModerationCommands(this, punishments).register(commands);
            VanishService vanish = new VanishService(this);
            listen(vanish);
            vanish.register(commands);
            staff = new StaffService(this, views);
            listen(staff);
            staff.register(commands);
        }
        if (module("world")) {
            WorldRules rules = new WorldRules(this);
            listen(rules);
            rules.start();
        }
        if (module("maintenance")) {
            maintenance = new MaintenanceService(this);
            maintenance.start();
            maintenance.register(commands);
        }
        if (module("restart")) {
            restart = new RestartService(this);
            listen(restart);
            restart.start();
            restart.register(commands);
        }
        if (module("starter-kit")) {
            StarterKit kit = new StarterKit(this);
            listen(kit);
            kit.register(commands);
        }
        if (module("info")) {
            InfoCommands info = new InfoCommands(this);
            info.start();
            info.register(commands);
        }
        if (module("admin")) {
            admin = new AdminChannel(this, warps);
            listen(admin);
            admin.start();
            admin.register(commands);
        }
        commands.install();
        Papi.register(this);
        players.adoptOnline();
        getLogger().info("Ready on " + (Tasks.FOLIA ? "Folia" : "Paper") + ": " + commands.all().size() + " commands, permissions "
                + (internalPerms ? "built-in" : "external"));
    }

    @Override
    public void onDisable() {
        if (players != null) {
            players.saveAllNow();
        }
        if (tab != null) {
            tab.stop();
        }
        if (perms != null) {
            perms.stop();
        }
        VaultHook.unregister(this);
        if (admin != null) {
            admin.stop();
        }
        if (tasks != null) {
            tasks.cancelAll();
        }
        if (database != null) {
            database.close();
        }
        instance = null;
    }

    public void reloadAll() {
        reloadConfig();
        messages.load(getConfig().getString("language", "ru"));
        perms.groups().load();
        perms.refreshRegistered();
        perms.applyAll();
    }

    public boolean module(String name) {
        return getConfig().getBoolean("modules." + name, true);
    }

    private void listen(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    public Messages msg() {
        return messages;
    }

    public Tasks tasks() {
        return tasks;
    }

    public Database db() {
        return database;
    }

    public PlayerService players() {
        return players;
    }

    public PermissionService perms() {
        return perms;
    }

    public CommandRegistry commands() {
        return commands;
    }

    public ChatService chat() {
        return chat;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public TabService tab() {
        return tab;
    }

    public PunishmentService punishments() {
        return punishments;
    }

    public StaffService staff() {
        return staff;
    }

    public RestartService restart() {
        return restart;
    }

    public AdminChannel admin() {
        return admin;
    }

    public MaintenanceService maintenance() {
        return maintenance;
    }
}
