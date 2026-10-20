package org.lts.anyplugin.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class Text {

    private static final MiniMessage SAFE = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolvers(StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(), StandardTags.rainbow(), StandardTags.reset())
                    .build())
            .build();
    private static final MiniMessage FULL = MiniMessage.miniMessage();
    private static final Pattern LEGACY = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
    private static final Pattern HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern URL = Pattern.compile("https?://[\\w.-]+(?:/[\\w./?%&=+#~:-]*)?");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"), Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"), Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"), Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"), Map.entry('r', "<reset>"));

    private Text() {
    }

    public static String legacyToMini(String input) {
        if (input == null) {
            return "";
        }
        String text = HEX.matcher(input).replaceAll("<#$1>");
        Matcher matcher = LEGACY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String tag = CODES.get(Character.toLowerCase(matcher.group(1).charAt(0)));
            matcher.appendReplacement(out, Matcher.quoteReplacement(tag == null ? "" : tag));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static Component userInput(String input, boolean colors) {
        if (!colors) {
            return Component.text(input);
        }
        return SAFE.deserialize(legacyToMini(input));
    }

    public static Component trusted(String input) {
        return FULL.deserialize(legacyToMini(input));
    }

    public static Component trusted(String input, TagResolver... resolvers) {
        return FULL.deserialize(legacyToMini(input), resolvers);
    }

    public static String sanitize(String input) {
        return FULL.serialize(SAFE.deserialize(legacyToMini(input)));
    }

    public static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String stripTags(String input) {
        return plain(trusted(input));
    }

    public static Component linkify(Component component) {
        return component.replaceText(TextReplacementConfig.builder()
                .match(URL)
                .replacement(builder -> builder.clickEvent(ClickEvent.openUrl(builder.content())).decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED))
                .build());
    }

    public static String date(long epochMillis) {
        return DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String escape(String input) {
        return FULL.escapeTags(input);
    }
}
