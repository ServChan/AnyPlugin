package org.lts.anyplugin.player;

public record Node(String kind, String node, boolean value, Long expires) {

    public static final String PERM = "perm";
    public static final String GROUP = "group";

    public boolean expired(long now) {
        return expires != null && expires <= now;
    }

    public boolean isGroup() {
        return GROUP.equals(kind);
    }
}
