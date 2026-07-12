package ru.myst.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Random;

/**
 * Одна запись в таблице лута миста.
 */
public class LootEntry {

    private final Material material;
    private final String displayName;
    private final int min;
    private final int max;
    private final int weight;

    public LootEntry(Material material, String displayName, int min, int max, int weight) {
        this.material = material;
        this.displayName = displayName;
        this.min = Math.max(1, min);
        this.max = Math.max(this.min, max);
        this.weight = Math.max(1, weight);
    }

    public int getWeight() {
        return weight;
    }

    public ItemStack roll(Random random) {
        int amount = min + (max > min ? random.nextInt(max - min + 1) : 0);
        ItemStack item = new ItemStack(material, amount);
        if (displayName != null && !displayName.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(displayName)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
            meta.displayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }
}
