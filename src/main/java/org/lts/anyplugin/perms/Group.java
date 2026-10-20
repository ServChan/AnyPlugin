package org.lts.anyplugin.perms;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Group {

    public final String name;
    public volatile int weight;
    public volatile String prefix = "";
    public volatile String suffix = "";
    public volatile String nameColor = "";
    public volatile String chatFormat = "";
    public final List<String> parents = new CopyOnWriteArrayList<>();
    public final Map<String, Boolean> permissions = new ConcurrentHashMap<>();

    public Group(String name) {
        this.name = name;
    }
}
