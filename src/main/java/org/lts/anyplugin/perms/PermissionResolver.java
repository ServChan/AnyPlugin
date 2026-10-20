package org.lts.anyplugin.perms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.lts.anyplugin.player.Node;

public final class PermissionResolver {

    private PermissionResolver() {
    }

    public static PermissionService.Resolved resolve(Group defaultGroup, Function<String, Group> lookup, List<Node> userNodes,
            String userPrefix, String userSuffix, long now) {
        List<Group> direct = new ArrayList<>();
        direct.add(defaultGroup);
        for (Node node : userNodes) {
            if (node.isGroup() && node.value() && !node.expired(now)) {
                Group group = lookup.apply(node.node());
                if (group != null && !direct.contains(group)) {
                    direct.add(group);
                }
            }
        }
        Set<Group> closure = new HashSet<>();
        Deque<Group> stack = new ArrayDeque<>(direct);
        while (!stack.isEmpty()) {
            Group group = stack.pop();
            if (!closure.add(group)) {
                continue;
            }
            for (String parent : group.parents) {
                Group p = lookup.apply(parent);
                if (p != null && !closure.contains(p)) {
                    stack.push(p);
                }
            }
        }
        List<Group> ordered = new ArrayList<>(closure);
        ordered.sort(Comparator.comparingInt((Group g) -> g.weight).thenComparing(g -> g.name));
        LinkedHashMap<String, Boolean> nodes = new LinkedHashMap<>();
        for (Group group : ordered) {
            group.permissions.forEach((node, value) -> put(nodes, node, value));
        }
        for (Node node : userNodes) {
            if (!node.isGroup() && !node.expired(now)) {
                put(nodes, node.node(), node.value());
            }
        }
        Group primary = direct.stream().max(Comparator.comparingInt((Group g) -> g.weight)).orElse(defaultGroup);
        String prefix = userPrefix;
        String suffix = userSuffix;
        for (int i = ordered.size() - 1; i >= 0 && (prefix == null || suffix == null); i--) {
            Group group = ordered.get(i);
            if (prefix == null && !group.prefix.isEmpty()) {
                prefix = group.prefix;
            }
            if (suffix == null && !group.suffix.isEmpty()) {
                suffix = group.suffix;
            }
        }
        return new PermissionService.Resolved(ordered, primary, nodes, prefix == null ? "" : prefix, suffix == null ? "" : suffix);
    }

    public static Map<String, Boolean> expand(Map<String, Boolean> nodes, List<String> registered) {
        LinkedHashMap<String, Boolean> out = new LinkedHashMap<>();
        List<Map.Entry<String, Boolean>> wildcards = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : nodes.entrySet()) {
            if (isWildcard(entry.getKey())) {
                wildcards.add(entry);
            }
        }
        wildcards.sort(Comparator.comparingInt(e -> depth(e.getKey())));
        for (Map.Entry<String, Boolean> wildcard : wildcards) {
            String key = wildcard.getKey();
            String base = key.equals("*") ? "" : key.substring(0, key.length() - 1);
            for (String name : registered) {
                if (name.startsWith(base)) {
                    put(out, name, wildcard.getValue());
                }
            }
            put(out, key, wildcard.getValue());
        }
        for (Map.Entry<String, Boolean> entry : nodes.entrySet()) {
            if (!isWildcard(entry.getKey())) {
                put(out, entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private static boolean isWildcard(String node) {
        return node.equals("*") || node.endsWith(".*");
    }

    static int depth(String node) {
        if (node.equals("*")) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < node.length(); i++) {
            if (node.charAt(i) == '.') {
                count++;
            }
        }
        return count;
    }

    private static void put(LinkedHashMap<String, Boolean> map, String node, boolean value) {
        map.remove(node);
        map.put(node, value);
    }
}
