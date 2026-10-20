package org.lts.anyplugin.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.regex.Pattern;
import org.lts.anyplugin.core.Durations;

final class AdminCommands {

    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    static final Pattern GROUP = Pattern.compile("[a-z0-9_\\-]{1,32}");
    static final Pattern NODE = Pattern.compile("[a-z0-9_.*\\-]{1,128}");
    static final Pattern WARP = Pattern.compile("[\\p{L}0-9_\\-]{1,24}");
    static final Pattern ITEM = Pattern.compile("[a-z0-9_]{1,64}");

    private AdminCommands() {
    }

    static String command(String op, JsonObject m) {
        return switch (op) {
            case "group.create" -> "perm group " + require(m, "group", GROUP) + " create";
            case "group.delete" -> "perm group " + require(m, "group", GROUP) + " delete";
            case "group.set" -> "perm group " + require(m, "group", GROUP) + " set " + node(m) + " " + bool(m, "value");
            case "group.unset" -> "perm group " + require(m, "group", GROUP) + " unset " + node(m);
            case "group.parent" -> "perm group " + require(m, "group", GROUP) + " parent " + (bool(m, "add") ? "add " : "remove ")
                    + require(m, "parent", GROUP);
            case "group.meta" -> {
                String field = oneOf(m, "field", "prefix", "suffix", "color", "weight");
                String value = text(m, "value", 120);
                if (field.equals("weight")) {
                    Integer.parseInt(value);
                }
                yield "perm group " + require(m, "group", GROUP) + " " + field + " " + (value.isEmpty() ? "clear" : value);
            }
            case "user.group" -> "perm user " + require(m, "name", NAME) + " group " + oneOf(m, "mode", "add", "remove", "set") + " "
                    + require(m, "group", GROUP) + duration(m);
            case "user.set" -> "perm user " + require(m, "name", NAME) + " set " + node(m) + " " + bool(m, "value") + duration(m);
            case "user.unset" -> "perm user " + require(m, "name", NAME) + " unset " + node(m);
            case "user.meta" -> {
                String value = text(m, "value", 120);
                yield "perm user " + require(m, "name", NAME) + " " + oneOf(m, "field", "prefix", "suffix") + " " + (value.isEmpty() ? "clear" : value);
            }
            case "punish" -> {
                String kind = oneOf(m, "kind", "ban", "ipban", "mute", "warn", "kick");
                String reason = text(m, "reason", 200);
                String duration = kind.equals("warn") || kind.equals("kick") ? "" : duration(m);
                yield kind + " " + require(m, "name", NAME) + duration + (reason.isEmpty() ? "" : " " + reason);
            }
            case "revoke" -> oneOf(m, "kind", "unban", "unmute") + " " + require(m, "name", NAME);
            case "unwarn" -> "unwarn " + Math.max(1, m.get("id").getAsLong());
            case "player.gamemode" -> "gm " + oneOf(m, "mode", "survival", "creative", "adventure", "spectator") + " " + require(m, "name", NAME);
            case "player.heal" -> "heal " + require(m, "name", NAME);
            case "player.feed" -> "feed " + require(m, "name", NAME);
            case "player.fly" -> "fly " + require(m, "name", NAME);
            case "player.freeze" -> "freeze " + require(m, "name", NAME);
            case "player.tpto" -> "minecraft:tp " + require(m, "name", NAME);
            case "player.tphere" -> "tphere " + require(m, "name", NAME);
            case "player.invsee" -> "invsee " + require(m, "name", NAME);
            case "player.enderchest" -> "enderchest " + require(m, "name", NAME);
            case "warp.tp" -> "warp " + require(m, "name", WARP);
            case "warp.delete" -> "delwarp " + require(m, "name", WARP);
            case "warp.create" -> {
                String description = text(m, "description", 120);
                yield "setwarp " + require(m, "name", WARP) + " " + require(m, "icon", ITEM) + (description.isEmpty() ? "" : " " + description);
            }
            case "server.maintenance" -> "maintenance " + (bool(m, "value") ? "on" : "off");
            case "server.restart" -> {
                String delay = string(m, "delay");
                if (!delay.equals("cancel") && Durations.parse(delay) <= 0) {
                    throw new IllegalArgumentException(delay);
                }
                yield "restart " + delay;
            }
            case "server.broadcast" -> {
                String value = text(m, "value", 200);
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("empty");
                }
                yield "broadcast " + value;
            }
            case "server.time" -> oneOf(m, "value", "day", "night");
            case "server.weather" -> oneOf(m, "value", "sun", "rain", "thunder");
            case "server.vanish" -> "vanish";
            case "server.reload" -> "anyplugin reload";
            default -> null;
        };
    }

    static String string(JsonObject m, String key) {
        JsonElement element = m.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(key);
        }
        return element.getAsString();
    }

    static String require(JsonObject m, String key, Pattern pattern) {
        String value = string(m, key);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(key);
        }
        return value;
    }

    private static String node(JsonObject m) {
        return require(m, "node", NODE);
    }

    private static boolean bool(JsonObject m, String key) {
        JsonElement element = m.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(key);
        }
        return element.getAsBoolean();
    }

    private static String oneOf(JsonObject m, String key, String... options) {
        String value = string(m, key).toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.equals(value)) {
                return value;
            }
        }
        throw new IllegalArgumentException(key);
    }

    private static String text(JsonObject m, String key, int max) {
        JsonElement element = m.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        String value = element.getAsString().replace('\n', ' ').replace('\r', ' ').strip();
        if (value.length() > max) {
            throw new IllegalArgumentException(key);
        }
        return value;
    }

    private static String duration(JsonObject m) {
        JsonElement element = m.get("duration");
        if (element == null || element.isJsonNull() || element.getAsString().isBlank()) {
            return "";
        }
        String value = element.getAsString().strip();
        if (Durations.parse(value) <= 0) {
            throw new IllegalArgumentException("duration");
        }
        return " " + value;
    }
}
