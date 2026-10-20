package org.lts.anyplugin.core;

import java.util.Locale;

public final class Durations {

    private Durations() {
    }

    public static long parse(String input) {
        if (input == null || input.isEmpty()) {
            return -1;
        }
        String text = input.toLowerCase(Locale.ROOT);
        long total = 0;
        long number = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                number = (number < 0 ? 0 : number) * 10 + (c - '0');
                if (number > 1_000_000) {
                    return -1;
                }
                continue;
            }
            if (number < 0) {
                return -1;
            }
            long unit = switch (c) {
                case 's', 'с' -> 1000L;
                case 'm', 'м' -> 60_000L;
                case 'h', 'ч' -> 3_600_000L;
                case 'd', 'д' -> 86_400_000L;
                case 'w', 'н' -> 604_800_000L;
                case 'y', 'г' -> 31_536_000_000L;
                default -> -1L;
            };
            if (unit < 0) {
                return -1;
            }
            total += number * unit;
            number = -1;
        }
        if (number >= 0) {
            total += number * 1000L;
        }
        return total > 0 ? total : -1;
    }

    public static boolean looksLikeDuration(String input) {
        return parse(input) > 0 && input.chars().anyMatch(Character::isLetter);
    }
}
