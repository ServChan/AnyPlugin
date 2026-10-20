package org.lts.anyplugin.command;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class CmdException extends RuntimeException {

    private final String key;
    private final TagResolver[] resolvers;

    public CmdException(String key, TagResolver... resolvers) {
        super(key, null, false, false);
        this.key = key;
        this.resolvers = resolvers;
    }

    public String key() {
        return key;
    }

    public TagResolver[] resolvers() {
        return resolvers;
    }
}
