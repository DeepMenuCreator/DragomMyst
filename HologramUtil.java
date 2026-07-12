package ru.myst.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;

public class HologramUtil {

    public static TextDisplay spawn(Location location, String legacyText) {
        Location holoLoc = location.clone().add(0.5, 1.6, 0.5);
        return location.getWorld().spawn(holoLoc, TextDisplay.class, td -> {
            td.text(toComponent(legacyText));
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setDefaultBackground(true);
            td.setPersistent(true);
            td.setInvulnerable(true);
        });
    }

    public static void update(TextDisplay display, String legacyText) {
        if (display != null && !display.isDead()) {
            display.text(toComponent(legacyText));
        }
    }

    public static void remove(TextDisplay display) {
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    public static Component toComponent(String legacyText) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    public static String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
