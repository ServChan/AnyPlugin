package org.lts.anyplugin.core;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Locations {

    private Locations() {
    }

    public static String serialize(Location location) {
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";"
                + location.getYaw() + ";" + location.getPitch();
    }

    public static Location deserialize(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String[] parts = text.split(";");
        if (parts.length < 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String brief(Location location) {
        return String.format(Locale.ROOT, "%s %d %d %d", location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
