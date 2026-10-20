package org.lts.anyplugin.player;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {

    public static Home of(String name, Location location) {
        return new Home(name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
