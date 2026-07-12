package ru.myst.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.myst.model.MystInstance;

public class MystInventoryHolder implements InventoryHolder {

    private final MystInstance instance;

    public MystInventoryHolder(MystInstance instance) {
        this.instance = instance;
    }

    public MystInstance getInstance() {
        return instance;
    }

    @Override
    public Inventory getInventory() {
        return instance.getInventory();
    }
}
