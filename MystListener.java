package ru.myst.listeners;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import ru.myst.MystPlugin;
import ru.myst.gui.LootEditorHolder;
import ru.myst.gui.MenuBuilder;
import ru.myst.gui.MystInventoryHolder;
import ru.myst.gui.MystMenuHolder;
import ru.myst.hologram.HologramUtil;
import ru.myst.manager.MystManager;
import ru.myst.model.MystInstance;
import ru.myst.model.MystType;
import ru.myst.util.TreasureUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MystListener implements Listener {

    private final MystPlugin plugin;
    private final MystManager manager;
    private final Map<UUID, Location> lastZoneCheck = new HashMap<>();

    public MystListener(MystPlugin plugin, MystManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ================= Открытие миста =================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.ENDER_CHEST) return;

        MystInstance instance = manager.findByBlockLocation(event.getClickedBlock().getLocation());
        if (instance == null) return; // обычный эндер-сундук игрока, не трогаем

        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        switch (instance.getState()) {
            case WAITING_OPEN -> player.sendMessage("§cЭтот мист ещё нельзя открывать! Осталось: §f"
                    + HologramUtil.formatTime(instance.secondsUntilOpen()));
            case CLOSED -> player.sendMessage("§cЭтот мист уже закрылся.");
            case OPEN -> player.openInventory(instance.getInventory());
        }
    }

    // ================= Забор сокровищ =================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        boolean topIsMyst = event.getView().getTopInventory().getHolder() instanceof MystInventoryHolder;
        if (topIsMyst) {
            handleMystChestClick(event);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof MystMenuHolder menuHolder) {
            handleMenuClick(event, menuHolder);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof LootEditorHolder editorHolder) {
            handleLootEditorClick(event, editorHolder);
        }
    }

    private void handleMystChestClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!(event.getClickedInventory().getHolder() instanceof MystInventoryHolder holder)) return;

        MystInstance instance = holder.getInstance();
        if (instance.getState() != MystInstance.State.OPEN) {
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        if (!TreasureUtil.isTreasure(plugin, clicked)) return;

        if (instance.isOnPickupCooldown(player.getUniqueId(), manager.getPickupCooldownMs())) {
            player.sendActionBar(Component.text("§7Подождите, прежде чем брать следующий предмет..."));
            return;
        }

        MystType type = instance.getType();
        MystType.ItemStackRoll roll = type.rollLoot(manager.getRandom());
        if (roll == null) {
            player.sendMessage("§cУ этого миста не настроен лут.");
            return;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(roll.item());
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        event.getClickedInventory().setItem(event.getSlot(), null);
        instance.setPickupCooldown(player.getUniqueId());
        player.sendActionBar(Component.text("§aВы получили сокровище!"));
    }

    private void handleMenuClick(InventoryClickEvent event, MystMenuHolder menuHolder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory()))
            return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MystType type = manager.getType(menuHolder.getTypeId());
        if (type == null) return;
        int slot = event.getSlot();
        boolean right = event.isRightClick();

        switch (slot) {
            case 10 -> { type.setSecurity(!type.isSecurity()); manager.saveTypes(); MenuBuilder.openMainMenu(player, manager, type.getId()); }
            case 11 -> { type.setKd(!type.isKd()); manager.saveTypes(); MenuBuilder.openMainMenu(player, manager, type.getId()); }
            case 12 -> { type.setUse(!type.isUse()); manager.saveTypes(); MenuBuilder.openMainMenu(player, manager, type.getId()); }
            case 14 -> {
                int v = type.getCountdownBeforeOpenSeconds() + (right ? -5 : 5);
                type.setCountdownBeforeOpenSeconds(Math.max(0, v));
                manager.saveTypes();
                MenuBuilder.openMainMenu(player, manager, type.getId());
            }
            case 15 -> {
                int v = type.getOpenDurationSeconds() + (right ? -30 : 30);
                type.setOpenDurationSeconds(Math.max(10, v));
                manager.saveTypes();
                MenuBuilder.openMainMenu(player, manager, type.getId());
            }
            case 22 -> MenuBuilder.openLootEditor(player, manager, type.getId());
            case 24 -> MenuBuilder.openPreview(player, manager, type.getId());
            case 26 -> {
                manager.deleteType(type.getId());
                player.closeInventory();
                player.sendMessage("§aТип миста §f" + type.getId() + " §aудалён.");
            }
            default -> {}
        }
    }

    private void handleLootEditorClick(InventoryClickEvent event, LootEditorHolder editorHolder) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!clickedTop) return; // разрешаем свободно брать предметы из своего инвентаря для наполнения редактора

        int slot = event.getSlot();
        if (slot == 49) {
            event.setCancelled(true);
            MenuBuilder.saveLootFromEditor(manager, editorHolder, event.getView().getTopInventory());
            player.sendMessage("§aЛут миста §f" + editorHolder.getTypeId() + " §aсохранён.");
            player.closeInventory();
        } else if (slot == 53) {
            event.setCancelled(true);
            player.closeInventory();
        } else if (slot >= 45) {
            event.setCancelled(true);
        }
        // слоты 0-44 - разрешаем обычное взаимодействие (класть/забирать предметы для лута)
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MystInventoryHolder
                || event.getView().getTopInventory().getHolder() instanceof MystMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // ничего не персистим специально - лут и так хранится в MystInstance.inventory
    }

    // ================= Защита зоны (security / use) =================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("myst.bypass")) return;

        MystInstance atBlock = manager.findByBlockLocation(event.getBlock().getLocation());
        if (atBlock != null) {
            event.setCancelled(true);
            player.sendMessage("§cЭтот блок защищён мистом.");
            return;
        }

        MystInstance zone = manager.getZoneAt(event.getBlock().getLocation());
        if (zone != null && zone.getType().isSecurity()) {
            event.setCancelled(true);
            player.sendMessage("§cЗдесь действует защита миста.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("myst.bypass")) return;
        MystInstance zone = manager.getZoneAt(event.getBlock().getLocation());
        if (zone != null && zone.getType().isSecurity()) {
            event.setCancelled(true);
            player.sendMessage("§cЗдесь действует защита миста.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;
        Player player = event.getPlayer();
        if (player.hasPermission("myst.bypass")) return;
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.ENDER_CHEST) return;

        MystInstance zone = manager.getZoneAt(player.getLocation());
        if (zone != null && !zone.getType().isUse()
                && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
        }
    }

    // ================= KD таймер при входе в зону =================

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        MystInstance zone = manager.getZoneAt(player.getLocation());
        if (zone == null || !zone.getType().isKd()) return;

        String text = switch (zone.getState()) {
            case WAITING_OPEN -> "§7Мист откроется через §f" + HologramUtil.formatTime(zone.secondsUntilOpen());
            case OPEN -> "§aМист закроется через §f" + HologramUtil.formatTime(zone.secondsUntilClose());
            case CLOSED -> "§7Мист закрыт";
        };
        player.sendActionBar(Component.text(text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastZoneCheck.remove(event.getPlayer().getUniqueId());
    }
}
