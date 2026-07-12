package ru.myst.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.myst.MystPlugin;
import ru.myst.hologram.HologramUtil;

import java.util.UUID;

public class TreasureUtil {

    private static NamespacedKey mystKey(MystPlugin plugin) {
        return new NamespacedKey(plugin, "myst_treasure_of");
    }

    /** Создаёт предмет-заглушку "Сокровище", привязанный к конкретному инстансу миста. */
    public static ItemStack createTreasure(MystPlugin plugin, Material material, String name, UUID mystUuid) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(HologramUtil.toComponent(name));
        meta.getPersistentDataContainer().set(mystKey(plugin), PersistentDataType.STRING, mystUuid.toString());
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isTreasure(MystPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(mystKey(plugin), PersistentDataType.STRING);
    }

    public static UUID getMystUuid(MystPlugin plugin, ItemStack item) {
        if (!isTreasure(plugin, item)) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(mystKey(plugin), PersistentDataType.STRING);
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
