package org.lts.anyplugin.perms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import org.lts.anyplugin.AnyPlugin;
import org.lts.anyplugin.command.CmdException;
import org.lts.anyplugin.command.CommandRegistry;
import org.lts.anyplugin.command.Ctx;
import org.lts.anyplugin.player.Node;
import org.lts.anyplugin.player.PlayerData;
import org.lts.anyplugin.player.PlayerService;

import static org.lts.anyplugin.core.Messages.mm;
import static org.lts.anyplugin.core.Messages.p;

public final class PermCommands {

    private final AnyPlugin plugin;
    private final PermissionService service;

    public PermCommands(AnyPlugin plugin, PermissionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void register(CommandRegistry registry) {
        registry.cmd("perm").alias("perms", "permissions").perm("anyplugin.perm").logged()
                .exec(this::run)
                .tab(this::complete);
    }

    private void run(Ctx ctx) {
        String section = ctx.arg(0).toLowerCase(Locale.ROOT);
        switch (section) {
            case "user", "u" -> user(ctx);
            case "group", "g" -> group(ctx);
            case "groups" -> groups(ctx);
            case "check" -> check(ctx);
            case "reload" -> {
                service.groups().load();
                service.refreshRegistered();
                service.applyAll();
                ctx.send("perm.reloaded");
            }
            default -> throw ctx.usage();
        }
    }

    private void groups(Ctx ctx) {
        ctx.send("perm.groups-header");
        for (Group group : service.groups().sorted()) {
            ctx.send("perm.groups-line", p("group", group.name), p("weight", group.weight), mm("prefix", group.prefix),
                    p("parents", group.parents.isEmpty() ? "-" : String.join(", ", group.parents)));
        }
    }

    private void check(Ctx ctx) {
        Player target = ctx.online(1);
        String node = ctx.arg(2).toLowerCase(Locale.ROOT);
        boolean set = target.isPermissionSet(node);
        boolean value = target.hasPermission(node);
        ctx.send(value ? "perm.check-true" : "perm.check-false", p("player", target.getName()), p("node", node),
                p("source", plugin.msg().raw(set ? "perm.check-set" : "perm.check-default")));
    }

    private void user(Ctx ctx) {
        requireInternal();
        String name = ctx.arg(1);
        String action = ctx.opt(2, "info").toLowerCase(Locale.ROOT);
        switch (action) {
            case "info" -> withUser(ctx, name, (uuid, realName) -> userInfo(ctx, uuid, realName));
            case "set" -> {
                String raw = ctx.arg(3).toLowerCase(Locale.ROOT);
                boolean negated = raw.startsWith("-") && raw.length() > 1;
                String node = negated ? raw.substring(1) : raw;
                boolean value = ctx.present(4) ? Boolean.parseBoolean(ctx.arg(4)) : !negated;
                Long expires = ctx.present(5) ? System.currentTimeMillis() + ctx.duration(5) : null;
                withUser(ctx, name, (uuid, realName) -> {
                    service.setNode(uuid, new Node(Node.PERM, node, value, expires));
                    ctx.send("perm.user-set", p("player", realName), p("node", node), p("value", value), p("expires", expiresText(expires)));
                });
            }
            case "unset" -> {
                String node = ctx.arg(3).toLowerCase(Locale.ROOT);
                withUser(ctx, name, (uuid, realName) -> {
                    service.unsetNode(uuid, Node.PERM, node);
                    ctx.send("perm.user-unset", p("player", realName), p("node", node));
                });
            }
            case "group" -> userGroup(ctx, name);
            case "prefix", "suffix" -> {
                String text = ctx.joinedOpt(3);
                String value = text.isEmpty() || text.equalsIgnoreCase("clear") ? null : text;
                withUser(ctx, name, (uuid, realName) -> {
                    plugin.players().setText(uuid, action, value);
                    PlayerData data = plugin.players().data(uuid);
                    if (data != null) {
                        if (action.equals("prefix")) {
                            data.prefix = value;
                        } else {
                            data.suffix = value;
                        }
                        service.applyLater(uuid);
                    }
                    ctx.send("perm.user-meta", p("player", realName), p("kind", action), mm("value", value == null ? plugin.msg().raw("perm.none") : value));
                });
            }
            case "clear" -> withUser(ctx, name, (uuid, realName) -> {
                PlayerData data = plugin.players().data(uuid);
                if (data != null) {
                    data.nodes.clear();
                }
                plugin.db().update("DELETE FROM user_nodes WHERE uuid=?", uuid.toString()).thenRun(() -> service.applyLater(uuid));
                ctx.send("perm.user-cleared", p("player", realName));
            });
            default -> throw ctx.usage();
        }
    }

    private void userGroup(Ctx ctx, String name) {
        String op = ctx.arg(3).toLowerCase(Locale.ROOT);
        Group group = service.groups().get(ctx.arg(4));
        if (group == null) {
            throw new CmdException("perm.group-not-found", p("group", ctx.arg(4)));
        }
        Long expires = ctx.present(5) ? System.currentTimeMillis() + ctx.duration(5) : null;
        withUser(ctx, name, (uuid, realName) -> {
            switch (op) {
                case "add" -> {
                    service.setNode(uuid, new Node(Node.GROUP, group.name, true, expires));
                    ctx.send("perm.user-group-add", p("player", realName), p("group", group.name), p("expires", expiresText(expires)));
                }
                case "remove" -> {
                    service.unsetNode(uuid, Node.GROUP, group.name);
                    ctx.send("perm.user-group-remove", p("player", realName), p("group", group.name));
                }
                case "set" -> {
                    PlayerData data = plugin.players().data(uuid);
                    if (data != null) {
                        data.nodes.removeIf(Node::isGroup);
                    }
                    plugin.db().update("DELETE FROM user_nodes WHERE uuid=? AND kind=?", uuid.toString(), Node.GROUP);
                    if (group != service.groups().defaultGroup()) {
                        service.setNode(uuid, new Node(Node.GROUP, group.name, true, expires));
                    } else {
                        service.applyLater(uuid);
                    }
                    ctx.send("perm.user-group-set", p("player", realName), p("group", group.name), p("expires", expiresText(expires)));
                }
                default -> ctx.send("usage.perm");
            }
        });
    }

    private void userInfo(Ctx ctx, UUID uuid, String realName) {
        PlayerData online = plugin.players().data(uuid);
        if (online != null) {
            printUser(ctx, realName, online);
            return;
        }
        plugin.db().call(c -> PlayerService.loadNodes(c, uuid)).thenAccept(nodes -> {
            PlayerData temp = new PlayerData(uuid, realName);
            temp.nodes.addAll(nodes);
            printUser(ctx, realName, temp);
        });
    }

    private void printUser(Ctx ctx, String realName, PlayerData data) {
        PermissionService.Resolved resolved = service.resolve(data);
        ctx.send("perm.user-info-header", p("player", realName));
        ctx.send("perm.user-info-groups", p("groups", String.join(", ", service.groupNames(data))), p("primary", resolved.primary().name));
        ctx.send("perm.user-info-prefix", mm("prefix", resolved.prefix()), mm("suffix", resolved.suffix()));
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Node node : data.nodes) {
            if (node.expired(now)) {
                continue;
            }
            any = true;
            ctx.send("perm.user-info-node", p("kind", node.kind()), p("node", node.node()), p("value", node.value()),
                    p("expires", expiresText(node.expires())));
        }
        if (!any) {
            ctx.send("perm.no-nodes");
        }
    }

    private void group(Ctx ctx) {
        requireInternal();
        String name = ctx.arg(1).toLowerCase(Locale.ROOT);
        String action = ctx.opt(2, "info").toLowerCase(Locale.ROOT);
        GroupManager manager = service.groups();
        if (action.equals("create")) {
            if (manager.get(name) != null) {
                throw new CmdException("perm.group-exists", p("group", name));
            }
            if (!name.matches("[a-z0-9_\\-]{1,32}")) {
                throw new CmdException("perm.group-bad-name");
            }
            manager.create(name);
            manager.save();
            ctx.send("perm.group-created", p("group", name));
            return;
        }
        Group group = manager.get(name);
        if (group == null) {
            throw new CmdException("perm.group-not-found", p("group", name));
        }
        switch (action) {
            case "info" -> {
                ctx.send("perm.group-info-header", p("group", group.name), p("weight", group.weight));
                ctx.send("perm.group-info-meta", mm("prefix", group.prefix), mm("suffix", group.suffix),
                        p("color", group.nameColor.isEmpty() ? "-" : group.nameColor),
                        p("parents", group.parents.isEmpty() ? "-" : String.join(", ", group.parents)));
                Map<String, Boolean> sorted = new TreeMap<>(group.permissions);
                if (sorted.isEmpty()) {
                    ctx.send("perm.no-nodes");
                }
                sorted.forEach((node, value) -> ctx.send("perm.group-info-node", p("node", node), p("value", value)));
                return;
            }
            case "delete" -> {
                if (!manager.delete(name)) {
                    throw new CmdException("perm.group-delete-default");
                }
                plugin.db().update("DELETE FROM user_nodes WHERE kind=? AND node=?", Node.GROUP, name);
                for (PlayerData data : plugin.players().all()) {
                    data.nodes.removeIf(n -> n.isGroup() && n.node().equals(name));
                }
                ctx.send("perm.group-deleted", p("group", name));
            }
            case "set" -> {
                String node = ctx.arg(3).toLowerCase(Locale.ROOT);
                boolean negated = node.startsWith("-") && node.length() > 1;
                if (negated) {
                    node = node.substring(1);
                }
                boolean value = ctx.present(4) ? Boolean.parseBoolean(ctx.arg(4)) : !negated;
                group.permissions.put(node, value);
                ctx.send("perm.group-set", p("group", name), p("node", node), p("value", value));
            }
            case "unset" -> {
                String node = ctx.arg(3).toLowerCase(Locale.ROOT);
                group.permissions.remove(node);
                ctx.send("perm.group-unset", p("group", name), p("node", node));
            }
            case "parent" -> {
                String op = ctx.arg(3).toLowerCase(Locale.ROOT);
                Group parent = manager.get(ctx.arg(4));
                if (parent == null) {
                    throw new CmdException("perm.group-not-found", p("group", ctx.arg(4)));
                }
                if (op.equals("add")) {
                    if (parent == group) {
                        throw new CmdException("perm.group-self-parent");
                    }
                    if (!group.parents.contains(parent.name)) {
                        group.parents.add(parent.name);
                    }
                } else if (op.equals("remove")) {
                    group.parents.remove(parent.name);
                } else {
                    throw ctx.usage();
                }
                ctx.send("perm.group-parent", p("group", name), p("parent", parent.name), p("op", op));
            }
            case "prefix" -> {
                group.prefix = cleared(ctx.joinedOpt(3));
                ctx.send("perm.group-meta", p("group", name), p("kind", "prefix"), mm("value", group.prefix));
            }
            case "suffix" -> {
                group.suffix = cleared(ctx.joinedOpt(3));
                ctx.send("perm.group-meta", p("group", name), p("kind", "suffix"), mm("value", group.suffix));
            }
            case "color" -> {
                group.nameColor = cleared(ctx.opt(3, ""));
                ctx.send("perm.group-meta", p("group", name), p("kind", "color"), p("value", group.nameColor));
            }
            case "weight" -> {
                group.weight = ctx.integer(3, -10000, 10000);
                ctx.send("perm.group-meta", p("group", name), p("kind", "weight"), p("value", group.weight));
            }
            default -> throw ctx.usage();
        }
        manager.save();
        service.applyAll();
    }

    private static String cleared(String text) {
        return text.equalsIgnoreCase("clear") ? "" : text;
    }

    private void requireInternal() {
        if (!service.internal()) {
            throw new CmdException("perm.external");
        }
    }

    private void withUser(Ctx ctx, String name, BiConsumer<UUID, String> action) {
        plugin.players().lookup(name).thenAccept(found -> {
            if (found.isEmpty()) {
                ctx.send("errors.player-never-joined", p("player", name));
                return;
            }
            action.accept(found.get().uuid(), found.get().name());
        }).exceptionally(e -> {
            plugin.getLogger().warning("perm lookup failed: " + e);
            ctx.send("errors.internal");
            return null;
        });
    }

    private String expiresText(Long expires) {
        if (expires == null) {
            return plugin.msg().raw("time.forever");
        }
        return plugin.msg().duration(expires - System.currentTimeMillis());
    }

    private List<String> complete(Ctx ctx) {
        List<String> groupNames = service.groups().names();
        int i = ctx.index();
        String section = ctx.opt(0, "").toLowerCase(Locale.ROOT);
        if (i == 0) {
            return List.of("user", "group", "groups", "check", "reload");
        }
        switch (section) {
            case "user", "u" -> {
                if (i == 1) {
                    return CommandRegistry.players(ctx.sender);
                }
                if (i == 2) {
                    return List.of("info", "set", "unset", "group", "prefix", "suffix", "clear");
                }
                String action = ctx.opt(2, "").toLowerCase(Locale.ROOT);
                if (action.equals("set") || action.equals("unset")) {
                    if (i == 3) {
                        return service.registeredNodes();
                    }
                    if (i == 4 && action.equals("set")) {
                        return List.of("true", "false");
                    }
                    if (i == 5) {
                        return List.of("1h", "1d", "7d", "30d");
                    }
                }
                if (action.equals("group")) {
                    if (i == 3) {
                        return List.of("add", "remove", "set");
                    }
                    if (i == 4) {
                        return groupNames;
                    }
                    if (i == 5) {
                        return List.of("1h", "1d", "7d", "30d");
                    }
                }
            }
            case "group", "g" -> {
                if (i == 1) {
                    return groupNames;
                }
                if (i == 2) {
                    return List.of("info", "create", "delete", "set", "unset", "parent", "prefix", "suffix", "color", "weight");
                }
                String action = ctx.opt(2, "").toLowerCase(Locale.ROOT);
                if ((action.equals("set") || action.equals("unset")) && i == 3) {
                    if (action.equals("unset")) {
                        Group group = service.groups().get(ctx.opt(1, ""));
                        return group == null ? List.of() : new ArrayList<>(group.permissions.keySet());
                    }
                    return service.registeredNodes();
                }
                if (action.equals("set") && i == 4) {
                    return List.of("true", "false");
                }
                if (action.equals("parent")) {
                    return i == 3 ? List.of("add", "remove") : groupNames;
                }
                if (action.equals("color") && i == 3) {
                    return List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray",
                            "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "clear");
                }
            }
            case "check" -> {
                if (i == 1) {
                    return CommandRegistry.players(ctx.sender);
                }
                if (i == 2) {
                    return service.registeredNodes();
                }
            }
            default -> {
                return List.of();
            }
        }
        return List.of();
    }
}
