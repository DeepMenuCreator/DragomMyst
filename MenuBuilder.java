package ru.myst.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.myst.hologram.HologramUtil;
import ru.myst.manager.MystManager;
import ru.myst.model.LootEntry;
import ru.myst.model.MystType;

import java.util.ArrayList;
import java.util.List;

public class MenuBuilder {

    public static void openMainMenu(Player player, MystManager manager, String typeId) {
        MystType type = manager.getType(typeId);
        if (type == null) {
            player.sendMessage("§cТакого миста не существует.");
            return;
        }
        MystMenuHolder holder = new MystMenuHolder(typeId);
        Inventory inv = Bukkit.createInventory(holder, 27,
                HologramUtil.toComponent("&8Настройка: " + type.getDisplayName()));
        holder.setInventory(inv);

        inv.setItem(10, toggleItem("Приват (security)", type.isSecurity(),
                "Создавать защиту зоны при спавне"));
        inv.setItem(11, toggleItem("Кулдаун-зона (kd)", type.isKd(),
                "Показывать таймер игрокам в зоне"));
        inv.setItem(12, toggleItem("Использование (use)", type.isUse(),
                "Разрешить использовать предметы/блоки в зоне"));

        inv.setItem(14, infoItem(Material.CLOCK, "&eВремя до открытия",
                List.of("&7Сейчас: &f" + type.getCountdownBeforeOpenSeconds() + " сек",
                        "&7ЛКМ: &a+5с &7ПКМ: &c-5с")));
        inv.setItem(15, infoItem(Material.SUNFLOWER, "&eВремя открытия (активности)",
                List.of("&7Сейчас: &f" + type.getOpenDurationSeconds() + " сек",
                        "&7ЛКМ: &a+30с &7ПКМ: &c-30с")));

        inv.setItem(20, infoItem(type.getTreasureItem(), "&6Предмет-сокровище",
                List.of("&7Имя: " + type.getTreasureName())));

        inv.setItem(22, infoItem(Material.CHEST, "&bРедактировать лут",
                List.of("&7Нажмите, чтобы открыть", "&7редактор лута миста")));

        inv.setItem(24, infoItem(Material.ENDER_EYE, "&dПревью лута",
                List.of("&7Посмотреть содержимое", "&7без редактирования")));

        inv.setItem(26, infoItem(Material.BARRIER, "&cУдалить тип миста", List.of("&7Осторожно!")));

        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler());
        }
    }

    public static void openLootEditor(Player player, MystManager manager, String typeId) {
        MystType type = manager.getType(typeId);
        if (type == null) return;
        LootEditorHolder holder = new LootEditorHolder(typeId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                HologramUtil.toComponent("&8Лут: " + type.getDisplayName()));
        holder.setInventory(inv);

        int slot = 0;
        for (LootEntry entry : type.getLoot()) {
            if (slot >= 45) break;
            inv.setItem(slot++, entry.roll(manager.getRandom()));
        }

        inv.setItem(49, infoItem(Material.LIME_WOOL, "&aСохранить",
                List.of("&7Количество предмета в слоте = вес/кол-во", "&7Пустые слоты игнорируются")));
        inv.setItem(53, infoItem(Material.RED_WOOL, "&cОтмена (без сохранения)", List.of()));
    }

    public static void openPreview(Player player, MystManager manager, String typeId) {
        MystType type = manager.getType(typeId);
        if (type == null) return;
        Inventory inv = Bukkit.createInventory(null, 54,
                HologramUtil.toComponent("&8Превью: " + type.getDisplayName()));
        int slot = 0;
        for (LootEntry entry : type.getLoot()) {
            if (slot >= 54) break;
            inv.setItem(slot++, entry.roll(manager.getRandom()));
        }
        player.openInventory(inv);
    }

    public static void saveLootFromEditor(MystManager manager, LootEditorHolder holder, Inventory inv) {
        MystType type = manager.getType(holder.getTypeId());
        if (type == null) return;
        List<LootEntry> newLoot = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            String name = "";
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                name = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                        .serialize(item.getItemMeta().displayName());
            }
            int amount = Math.max(1, item.getAmount());
            newLoot.add(new LootEntry(item.getType(), name, amount, amount, amount));
        }
        type.getLoot().clear();
        type.getLoot().addAll(newLoot);
        manager.saveTypes();
    }

    private static ItemStack toggleItem(String name, boolean value, String desc) {
        Material mat = value ? Material.LIME_DYE : Material.GRAY_DYE;
        return infoItem(mat, (value ? "&a" : "&7") + name + (value ? " &a[ВКЛ]" : " &c[ВЫКЛ]"),
                List.of("&7" + desc, "&eНажмите, чтобы переключить"));
    }

    private static ItemStack infoItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(HologramUtil.toComponent(name));
        List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(HologramUtil.toComponent(line));
        }
        meta.lore(loreComponents);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(HologramUtil.toComponent(" "));
        item.setItemMeta(meta);
        return item;
    }
}
