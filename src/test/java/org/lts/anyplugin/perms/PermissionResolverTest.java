package org.lts.anyplugin.perms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.lts.anyplugin.player.Node;

class PermissionResolverTest {

    private static final long NOW = 1_000_000L;

    private final Map<String, Group> groups = new HashMap<>();
    private final Group defaultGroup = group("default", 0);

    private Group group(String name, int weight, String... permissions) {
        Group group = new Group(name);
        group.weight = weight;
        for (String permission : permissions) {
            GroupManager.putNode(group.permissions, permission);
        }
        groups.put(name, group);
        return group;
    }

    private PermissionService.Resolved resolve(String prefix, String suffix, Node... nodes) {
        return PermissionResolver.resolve(defaultGroup, groups::get, List.of(nodes), prefix, suffix, NOW);
    }

    private PermissionService.Resolved resolve(Node... nodes) {
        return resolve(null, null, nodes);
    }

    private static Node member(String group) {
        return new Node(Node.GROUP, group, true, null);
    }

    private static Node perm(String node, boolean value) {
        return new Node(Node.PERM, node, value, null);
    }

    private static List<String> names(List<Group> list) {
        List<String> out = new ArrayList<>();
        list.forEach(g -> out.add(g.name));
        return out;
    }

    @Test
    void everyoneHasTheDefaultGroup() {
        defaultGroup.permissions.put("anyplugin.player", true);
        PermissionService.Resolved resolved = resolve();
        assertEquals(List.of("default"), names(resolved.groups()));
        assertEquals(defaultGroup, resolved.primary());
        assertEquals(Map.of("anyplugin.player", true), resolved.nodes());
    }

    @Test
    void inheritsParentsTransitively() {
        Group friend = group("friend", 10, "anyplugin.craft");
        friend.parents.add("default");
        Group moder = group("moder", 50, "anyplugin.moderator");
        moder.parents.add("friend");
        defaultGroup.permissions.put("anyplugin.player", true);
        PermissionService.Resolved resolved = resolve(member("moder"));
        assertEquals(List.of("default", "friend", "moder"), names(resolved.groups()));
        assertEquals(List.of("anyplugin.player", "anyplugin.craft", "anyplugin.moderator"), new ArrayList<>(resolved.nodes().keySet()));
    }

    @Test
    void parentCyclesTerminate() {
        Group a = group("a", 10, "node.a");
        Group b = group("b", 20, "node.b");
        a.parents.add("b");
        b.parents.add("a");
        Group self = group("self", 5, "node.self");
        self.parents.add("self");
        PermissionService.Resolved resolved = resolve(member("a"), member("self"));
        assertEquals(List.of("default", "self", "a", "b"), names(resolved.groups()));
        assertEquals(3, resolved.nodes().size());
    }

    @Test
    void unknownGroupsAndParentsAreIgnored() {
        Group friend = group("friend", 10, "node.friend");
        friend.parents.add("ghost");
        PermissionService.Resolved resolved = resolve(member("nobody"), member("friend"));
        assertEquals(List.of("default", "friend"), names(resolved.groups()));
    }

    @Test
    void heavierGroupOverridesLighterGroup() {
        group("low", 10, "-anyplugin.fly");
        group("high", 20, "anyplugin.fly");
        assertEquals(true, resolve(member("high"), member("low")).nodes().get("anyplugin.fly"));
        groups.get("low").weight = 30;
        assertEquals(false, resolve(member("high"), member("low")).nodes().get("anyplugin.fly"));
    }

    @Test
    void equalWeightsAreOrderedByName() {
        group("beta", 10, "anyplugin.fly");
        group("alpha", 10, "-anyplugin.fly");
        PermissionService.Resolved resolved = resolve(member("beta"), member("alpha"));
        assertEquals(List.of("default", "alpha", "beta"), names(resolved.groups()));
        assertEquals(true, resolved.nodes().get("anyplugin.fly"));
    }

    @Test
    void userNodesOverrideGroupNodes() {
        group("admin", 100, "anyplugin.ban", "-anyplugin.vanish");
        PermissionService.Resolved resolved = resolve(member("admin"), perm("anyplugin.ban", false), perm("anyplugin.vanish", true));
        assertEquals(false, resolved.nodes().get("anyplugin.ban"));
        assertEquals(true, resolved.nodes().get("anyplugin.vanish"));
    }

    @Test
    void negationIsParsedFromGroupFiles() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        GroupManager.putNode(map, " -AnyPlugin.Fly ");
        GroupManager.putNode(map, "anyplugin.heal");
        GroupManager.putNode(map, "   ");
        assertEquals(Map.of("anyplugin.fly", false, "anyplugin.heal", true), map);
    }

    @Test
    void expiredNodesAreIgnored() {
        group("vip", 10, "anyplugin.vip");
        PermissionService.Resolved expired = resolve(new Node(Node.GROUP, "vip", true, NOW), new Node(Node.PERM, "anyplugin.fly", true, NOW - 1));
        assertEquals(List.of("default"), names(expired.groups()));
        assertFalse(expired.nodes().containsKey("anyplugin.fly"));
        PermissionService.Resolved active = resolve(new Node(Node.GROUP, "vip", true, NOW + 1), new Node(Node.PERM, "anyplugin.fly", true, NOW + 1));
        assertEquals(List.of("default", "vip"), names(active.groups()));
        assertEquals(true, active.nodes().get("anyplugin.fly"));
    }

    @Test
    void negativeGroupMembershipGrantsNothing() {
        group("vip", 10, "anyplugin.vip");
        PermissionService.Resolved resolved = resolve(new Node(Node.GROUP, "vip", false, null));
        assertEquals(List.of("default"), names(resolved.groups()));
    }

    @Test
    void primaryIsTheHeaviestDirectGroup() {
        Group admin = group("admin", 100);
        Group moder = group("moder", 50);
        moder.parents.add("admin");
        group("friend", 10);
        assertEquals("moder", resolve(member("friend"), member("moder")).primary().name);
        assertEquals("admin", resolve(member("admin"), member("moder")).primary().name);
        assertTrue(resolve(member("moder")).groups().contains(admin));
    }

    @Test
    void defaultGroupWinsWeightTies() {
        group("zero", 0);
        assertEquals("default", resolve(member("zero")).primary().name);
    }

    @Test
    void prefixComesFromTheHeaviestGroupThatHasOne() {
        defaultGroup.prefix = "<gray>";
        Group friend = group("friend", 10);
        friend.prefix = "[Friend] ";
        friend.suffix = " *";
        Group moder = group("moder", 50);
        moder.suffix = " !";
        PermissionService.Resolved resolved = resolve(member("friend"), member("moder"));
        assertEquals("[Friend] ", resolved.prefix());
        assertEquals(" !", resolved.suffix());
        assertEquals("<gray>", resolve().prefix());
        assertEquals("", resolve().suffix());
    }

    @Test
    void ownPrefixBeatsGroupPrefix() {
        Group friend = group("friend", 10);
        friend.prefix = "[Friend] ";
        friend.suffix = " *";
        PermissionService.Resolved resolved = resolve("[Me] ", null, member("friend"));
        assertEquals("[Me] ", resolved.prefix());
        assertEquals(" *", resolved.suffix());
        assertEquals("", resolve("", "", member("friend")).prefix());
    }

    @Test
    void negatedNodeBeatsStar() {
        Map<String, Boolean> nodes = new LinkedHashMap<>();
        nodes.put("anyplugin.x", false);
        nodes.put("*", true);
        Map<String, Boolean> out = PermissionResolver.expand(nodes, List.of("anyplugin.x", "anyplugin.y", "other.z"));
        assertEquals(false, out.get("anyplugin.x"));
        assertEquals(true, out.get("anyplugin.y"));
        assertEquals(true, out.get("other.z"));
        assertEquals(true, out.get("*"));
        assertEquals("anyplugin.x", lastKey(out));
    }

    @Test
    void deeperWildcardBeatsShallowerWildcard() {
        Map<String, Boolean> nodes = new LinkedHashMap<>();
        nodes.put("anyplugin.admin.*", true);
        nodes.put("anyplugin.*", false);
        nodes.put("*", true);
        Map<String, Boolean> out = PermissionResolver.expand(nodes,
                List.of("anyplugin.admin.reload", "anyplugin.fly", "minecraft.command.tp"));
        assertEquals(true, out.get("anyplugin.admin.reload"));
        assertEquals(false, out.get("anyplugin.fly"));
        assertEquals(true, out.get("minecraft.command.tp"));
        assertEquals(List.of("minecraft.command.tp", "*", "anyplugin.fly", "anyplugin.*", "anyplugin.admin.reload", "anyplugin.admin.*"),
                new ArrayList<>(out.keySet()));
    }

    @Test
    void wildcardMatchesOnlyItsOwnBranch() {
        Map<String, Boolean> out = PermissionResolver.expand(Map.of("anyplugin.home.*", true),
                List.of("anyplugin.home.set", "anyplugin.homes.5", "anyplugin.home"));
        assertEquals(Map.of("anyplugin.home.set", true, "anyplugin.home.*", true), out);
    }

    @Test
    void wildcardsAreKeptWithoutRegisteredNodes() {
        Map<String, Boolean> out = PermissionResolver.expand(Map.of("*", true), List.of());
        assertEquals(Map.of("*", true), out);
    }

    @Test
    void depthCountsSegments() {
        assertEquals(0, PermissionResolver.depth("*"));
        assertEquals(2, PermissionResolver.depth("anyplugin.*"));
        assertEquals(3, PermissionResolver.depth("anyplugin.admin.*"));
    }

    private static String lastKey(Map<String, Boolean> map) {
        String last = null;
        for (String key : map.keySet()) {
            last = key;
        }
        return last;
    }
}
