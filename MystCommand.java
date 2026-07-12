package ru.myst.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.myst.MystPlugin;
import ru.myst.gui.MenuBuilder;
import ru.myst.hologram.HologramUtil;
import ru.myst.manager.MystManager;
import ru.myst.model.MystInstance;
import ru.myst.model.MystType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MystCommand implements CommandExecutor, TabCompleter {

    private final MystPlugin plugin;
    private final MystManager manager;

    public MystCommand(MystPlugin plugin, MystManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "delay" -> cmdDelay(sender);
            case "pos" -> cmdPos(sender);
            case "menu" -> cmdMenu(sender, args);
            case "reload" -> cmdReload(sender);
            case "preview" -> cmdPreview(sender, args);
            case "security" -> cmdBoolToggle(sender, args, "security");
            case "kd" -> cmdBoolToggle(sender, args, "kd");
            case "use" -> cmdBoolToggle(sender, args, "use");
            case "inventory" -> cmdInventory(sender, args);
            case "create" -> cmdCreate(sender, args);
            case "editholo" -> cmdEditHolo(sender, args);
            case "spawn" -> cmdSpawn(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("""
                §6§l== Myst Plugin ==
                §e/myst delay §7- ближайший мистик (время)
                §e/myst pos §7- координаты ближайшего мистика
                §e/myst menu <имя> §7- меню настройки миста
                §e/myst reload §7- перезагрузить конфиг
                §e/myst preview <имя> §7- превью лута
                §e/myst security <имя> <on/off>
                §e/myst kd <имя> <on/off>
                §e/myst use <имя> <on/off>
                §e/myst inventory <имя> §7- редактор лута
                §e/myst create <имя> §7- создать тип миста
                §e/myst editholo <имя> <текст> §7- голограмма имени
                §e/myst spawn <имя> §7- заспавнить мист под ногами
                """);
    }

    private boolean noPerm(CommandSender sender) {
        if (!sender.hasPermission("myst.admin")) {
            sender.sendMessage("§cНедостаточно прав.");
            return true;
        }
        return false;
    }

    private void cmdDelay(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько для игроков."); return; }
        MystInstance nearest = manager.findNearest(player.getLocation());
        if (nearest == null) { sender.sendMessage("§7Активных мистов не найдено."); return; }
        String time = switch (nearest.getState()) {
            case WAITING_OPEN -> "откроется через " + HologramUtil.formatTime(nearest.secondsUntilOpen());
            case OPEN -> "закроется через " + HologramUtil.formatTime(nearest.secondsUntilClose());
            case CLOSED -> "уже закрыт";
        };
        sender.sendMessage("§aБлижайший мист (" + nearest.getType().getId() + ") " + time + ".");
    }

    private void cmdPos(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько для игроков."); return; }
        MystInstance nearest = manager.findNearest(player.getLocation());
        if (nearest == null) { sender.sendMessage("§7Активных мистов не найдено."); return; }
        var loc = nearest.getLocation();
        sender.sendMessage("§aКоординаты ближайшего миста: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + " §7(мир: " + loc.getWorld().getName() + ")");
    }

    private void cmdMenu(CommandSender sender, String[] args) {
        if (noPerm(sender)) return;
        if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько для игроков."); return; }
        if (args.length < 2) { sender.sendMessage("§cИспользование: /myst menu <имя>"); return; }
        String typeId = args[1];
        if (!manager.typeExists(typeId)) { sender.sendMessage("§cТакого типа миста нет. Создайте: /myst create " + typeId); return; }
        MenuBuilder.openMainMenu(player, manager, typeId);
    }

    private void cmdReload(CommandSender sender) {
        if (noPerm(sender)) return;
        manager.loadAll();
        sender.sendMessage("§aКонфигурация Myst перезагружена.");
    }

    private void cmdPreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько для игроков."); return; }
        if (args.length < 2) { sender.sendMessage("§cИспользование: /myst preview <имя>"); return; }
        String typeId = args[1];
        if (!manager.typeExists(typeId)) { sender.sendMessage("§cТакого типа миста нет."); return; }
        MenuBuilder.openPreview(player, manager, typeId);
    }

    private void cmdBoolToggle(CommandSender sender, String[] args, String field) {
        if (noPerm(sender)) return;
        if (args.length < 3) { sender.sendMessage("§cИспользование: /myst " + field + " <имя> <on/off>"); return; }
        MystType type = manager.getType(args[1]);
        if (type == null) { sender.sendMessage("§cТакого типа миста нет."); return; }
        boolean value = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true");
        switch (field) {
            case "security" -> type.setSecurity(value);
            case "kd" -> type.setKd(value);
            case "use" -> type.setUse(value);
        }
        manager.saveTypes();
        sender.sendMessage("§a" + field + " для миста §f" + type.getId() + " §aустановлено в " + (value ? "ON" : "OFF"));
    }

    private void cmdInventory(CommandSender sender, String[] args) {
        if (noPerm(sender)) return;
        if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько для игроков."); return; }
        if (args.length < 2) { sender.sendMessage("§cИспользование: /myst inventory <имя>"); return; }
        String typeId = args[1];
        if (!manager.typeExists(typeId)) { sender.sendMessage("§cТакого типа миста нет."); return; }
        MenuBuilder.openLootEditor(player, manager, typeId);
    }

    private void cmdCreate(CommandSender sender, String[] args) {
        if (noPerm(sender)) return;
        if (args.length < 2) { sender.sendMessage("§cИспользование: /myst create <имя>"); return; }
        String typeId = args[1];
        if (manager.typeExists(typeId)) { sender.sendMessage("§cТип миста с таким именем уже существует."); return; }
        manager.createType(typeId);
        sender.sendMessage("§aТип миста §f" + typeId + " §aсоздан. Настройте его: /myst menu " + typeId);
    }

    private void cmdEditHolo(CommandSender sender, String[] args) {
        if (noPerm(sender)) return;
        if (args.length < 3) { sender.sendMessage("§cИспользование: /myst editholo <имя> <текст с & кодами и HEX &#RRGGBB>"); return; }
        MystType type = manager.getType(args[1]);
        if (type == null) { sender.sendMessage("§cТакого типа миста нет."); return; }
        String text = String.join(" ", List.of(args).subList(2, args.length));
        type.setHologramFormat(text);
        manager.saveTypes();
        sender.sendMessage("§aГолограмма имени миста §f" + type.getId() + " §aобновлена.");
    }

    private void cmdSpawn(CommandSender sender, String[] args) {
        if (noPerm(sender)) return;
        if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько для игроков."); return; }
        if (args.length < 2) { sender.sendMessage("§cИспользование: /myst spawn <имя>"); return; }
        String typeId = args[1];
        var result = manager.spawnMyst(typeId, player.getLocation());
        switch (result) {
            case OK -> sender.sendMessage("§aМист §f" + typeId + " §aзаспавнен.");
            case TOO_CLOSE -> sender.sendMessage("§cНельзя заспавнить мист так близко к другому мисту!");
            case UNKNOWN_TYPE -> sender.sendMessage("§cТакого типа миста нет.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("delay", "pos", "menu", "reload", "preview", "security", "kd", "use",
                            "inventory", "create", "editholo", "spawn").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return manager.getTypes().stream().map(MystType::getId)
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && List.of("security", "kd", "use").contains(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("on", "off");
        }
        return new ArrayList<>();
    }
}
