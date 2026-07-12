package ru.myst.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import ru.myst.MystPlugin;
import ru.myst.hologram.HologramUtil;
import ru.myst.model.LootEntry;
import ru.myst.model.MystInstance;
import ru.myst.model.MystType;
import ru.myst.util.TreasureUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MystManager {

    private final MystPlugin plugin;
    private final Map<String, MystType> types = new LinkedHashMap<>();
    private final List<MystInstance> active = new ArrayList<>();
    private final Random random = new Random();

    private File dataFile;
    private FileConfiguration dataConfig;

    private long pickupCooldownMs;
    private int noDoubleSpawnRadius;
    private int zoneRadius;

    private BukkitTask tickTask;

    public MystManager(MystPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== ЗАГРУЗКА / СОХРАНЕНИЕ ====================

    public void loadAll() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        pickupCooldownMs = cfg.getLong("settings.pickup-cooldown-ms", 1000);
        noDoubleSpawnRadius = cfg.getInt("settings.no-double-spawn-radius", 15);
        zoneRadius = cfg.getInt("settings.zone-radius", 6);

        types.clear();
        ConfigurationSection typesSection = cfg.getConfigurationSection("myst-types");
        if (typesSection != null) {
            for (String id : typesSection.getKeys(false)) {
                ConfigurationSection s = typesSection.getConfigurationSection(id);
                if (s == null) continue;
                types.put(id.toLowerCase(Locale.ROOT), loadType(id, s));
            }
        }

        loadActiveMysts();
    }

    private MystType loadType(String id, ConfigurationSection s) {
        MystType type = new MystType(id);
        type.setDisplayName(s.getString("display-name", "&a" + id));
        type.setHologramFormat(s.getString("hologram-format", "&e&l{name}"));
        type.setHologramOpeningFormat(s.getString("hologram-opening-format", "&7Откроется через: &f{time}"));
        type.setHologramOpenFormat(s.getString("hologram-open-format", "&aЗакроется через: &f{time}"));
        type.setCountdownBeforeOpenSeconds(s.getInt("countdown-before-open-seconds", 15));
        type.setOpenDurationSeconds(s.getInt("open-duration-seconds", 300));
        type.setSecurity(s.getBoolean("security", true));
        type.setKd(s.getBoolean("kd", true));
        type.setUse(s.getBoolean("use", false));
        Material treasureMat = Material.matchMaterial(s.getString("treasure-item", "PHANTOM_MEMBRANE"));
        type.setTreasureItem(treasureMat != null ? treasureMat : Material.PHANTOM_MEMBRANE);
        type.setTreasureName(s.getString("treasure-name", "&6Сокровище"));

        List<Map<?, ?>> lootRaw = s.getMapList("loot");
        for (Map<?, ?> m : lootRaw) {
            try {
                Material mat = Material.matchMaterial(String.valueOf(m.get("item")));
                if (mat == null) continue;
                String name = m.get("name") != null ? String.valueOf(m.get("name")) : "";
                int min = m.get("min") != null ? Integer.parseInt(String.valueOf(m.get("min"))) : 1;
                int max = m.get("max") != null ? Integer.parseInt(String.valueOf(m.get("max"))) : min;
                int weight = m.get("weight") != null ? Integer.parseInt(String.valueOf(m.get("weight"))) : 1;
                type.getLoot().add(new LootEntry(mat, name, min, max, weight));
            } catch (Exception ignored) {
            }
        }
        return type;
    }

    public void saveTypes() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("myst-types", null);
        for (MystType type : types.values()) {
            String base = "myst-types." + type.getId();
            cfg.set(base + ".display-name", type.getDisplayName());
            cfg.set(base + ".hologram-format", type.getHologramFormat());
            cfg.set(base + ".hologram-opening-format", type.getHologramOpeningFormat());
            cfg.set(base + ".hologram-open-format", type.getHologramOpenFormat());
            cfg.set(base + ".countdown-before-open-seconds", type.getCountdownBeforeOpenSeconds());
            cfg.set(base + ".open-duration-seconds", type.getOpenDurationSeconds());
            cfg.set(base + ".security", type.isSecurity());
            cfg.set(base + ".kd", type.isKd());
            cfg.set(base + ".use", type.isUse());
            cfg.set(base + ".treasure-item", type.getTreasureItem().name());
            cfg.set(base + ".treasure-name", type.getTreasureName());

            List<Map<String, Object>> lootList = new ArrayList<>();
            for (LootEntry entry : type.getLoot()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("item", entry.getMaterial().name());
                m.put("name", entry.getDisplayName());
                m.put("min", entry.getMin());
                m.put("max", entry.getMax());
                m.put("weight", entry.getWeight());
                lootList.add(m);
            }
            cfg.set(base + ".loot", lootList);
        }
        plugin.saveConfig();
    }

    private void loadActiveMysts() {
        dataFile = new File(plugin.getDataFolder(), "active-mysts.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось создать active-mysts.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        active.clear();
        ConfigurationSection section = dataConfig.getConfigurationSection("mysts");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            String typeId = s.getString("type");
            MystType type = types.get(typeId == null ? "" : typeId.toLowerCase(Locale.ROOT));
            if (type == null) continue;
            World world = Bukkit.getWorld(s.getString("world", ""));
            if (world == null) continue;
            Location loc = new Location(world, s.getInt("x"), s.getInt("y"), s.getInt("z"));
            MystInstance instance = new MystInstance(type, loc);
            String stateName = s.getString("state", "WAITING_OPEN");
            long changedAt = s.getLong("stateChangedAt", System.currentTimeMillis());
            MystInstance.State parsedState = MystInstance.State.WAITING_OPEN;
            try {
                parsedState = MystInstance.State.valueOf(stateName);
            } catch (Exception ignored) {
            }
            instance.restoreState(parsedState, changedAt);
            respawnPhysical(instance);
            active.add(instance);
        }
    }

    public void saveActiveMysts() {
        if (dataConfig == null || dataFile == null) return;
        dataConfig.set("mysts", null);
        int i = 0;
        for (MystInstance instance : active) {
            String base = "mysts.m" + (i++);
            dataConfig.set(base + ".type", instance.getType().getId());
            dataConfig.set(base + ".world", instance.getLocation().getWorld().getName());
            dataConfig.set(base + ".x", instance.getLocation().getBlockX());
            dataConfig.set(base + ".y", instance.getLocation().getBlockY());
            dataConfig.set(base + ".z", instance.getLocation().getBlockZ());
            dataConfig.set(base + ".state", instance.getState().name());
            dataConfig.set(base + ".stateChangedAt", instance.getStateChangedAt());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить active-mysts.yml: " + e.getMessage());
        }
    }

    // ==================== ТИПЫ ====================

    public boolean typeExists(String id) {
        return types.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public MystType getType(String id) {
        return types.get(id.toLowerCase(Locale.ROOT));
    }

    public MystType createType(String id) {
        MystType type = new MystType(id);
        types.put(id.toLowerCase(Locale.ROOT), type);
        saveTypes();
        return type;
    }

    public void deleteType(String id) {
        types.remove(id.toLowerCase(Locale.ROOT));
        saveTypes();
    }

    public Collection<MystType> getTypes() {
        return types.values();
    }

    // ==================== СПАВН ====================

    public enum SpawnResult { OK, TOO_CLOSE, UNKNOWN_TYPE }

    public SpawnResult spawnMyst(String typeId, Location location) {
        MystType type = getType(typeId);
        if (type == null) return SpawnResult.UNKNOWN_TYPE;

        for (MystInstance existing : active) {
            if (existing.getLocation().getWorld().equals(location.getWorld())
                    && existing.getLocation().distance(location) < noDoubleSpawnRadius) {
                return SpawnResult.TOO_CLOSE;
            }
        }

        MystInstance instance = new MystInstance(type, location.getBlock().getLocation());
        instance.setState(MystInstance.State.WAITING_OPEN);
        respawnPhysical(instance);
        active.add(instance);
        saveActiveMysts();
        return SpawnResult.OK;
    }

    /** Ставит блок эндер-сундука и голограмму для инстанса (используется при спавне и при загрузке с рестарта). */
    private void respawnPhysical(MystInstance instance) {
        Location loc = instance.getLocation();
        loc.getBlock().setType(Material.ENDER_CHEST);
        String text = renderHologramText(instance);
        instance.setHologram(HologramUtil.spawn(loc, text));
        instance.setInventory(buildInventory(instance));
    }

    private Inventory buildInventory(MystInstance instance) {
        Inventory inv = Bukkit.createInventory(new ru.myst.gui.MystInventoryHolder(instance), 54,
                HologramUtil.toComponent(instance.getType().getDisplayName()));
        MystType type = instance.getType();
        var treasure = TreasureUtil.createTreasure(plugin, type.getTreasureItem(), type.getTreasureName(), instance.getUuid());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, treasure.clone());
        }
        return inv;
    }

    private String renderHologramText(MystInstance instance) {
        MystType type = instance.getType();
        return switch (instance.getState()) {
            case WAITING_OPEN -> type.getHologramFormat().replace("{name}", type.getDisplayName())
                    + "\n" + type.getHologramOpeningFormat().replace("{time}", HologramUtil.formatTime(instance.secondsUntilOpen()));
            case OPEN -> type.getHologramFormat().replace("{name}", type.getDisplayName())
                    + "\n" + type.getHologramOpenFormat().replace("{time}", HologramUtil.formatTime(instance.secondsUntilClose()));
            case CLOSED -> type.getHologramFormat().replace("{name}", type.getDisplayName());
        };
    }

    // ==================== ТИК ====================

    public void startTicking() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stopTicking() {
        if (tickTask != null) tickTask.cancel();
    }

    private void tick() {
        Iterator<MystInstance> it = active.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            MystInstance instance = it.next();
            switch (instance.getState()) {
                case WAITING_OPEN -> {
                    if (instance.secondsUntilOpen() <= 0) {
                        instance.setState(MystInstance.State.OPEN);
                        changed = true;
                    }
                }
                case OPEN -> {
                    if (instance.secondsUntilClose() <= 0) {
                        instance.setState(MystInstance.State.CLOSED);
                        changed = true;
                        // закрываем инвентарь для всех, кто в нём находится
                        for (Player viewer : new ArrayList<>(instance.getInventory().getViewers().stream()
                                .filter(h -> h instanceof Player).map(h -> (Player) h).toList())) {
                            viewer.closeInventory();
                        }
                    }
                }
                case CLOSED -> {
                    // после короткой паузы полностью убираем мист
                    long elapsed = (System.currentTimeMillis() - instance.getStateChangedAt()) / 1000L;
                    if (elapsed >= 5) {
                        despawn(instance);
                        it.remove();
                        changed = true;
                        continue;
                    }
                }
            }
            HologramUtil.update(instance.getHologram(), renderHologramText(instance));
        }
        if (changed) saveActiveMysts();
    }

    private void despawn(MystInstance instance) {
        HologramUtil.remove(instance.getHologram());
        Location loc = instance.getLocation();
        if (loc.getBlock().getType() == Material.ENDER_CHEST) {
            loc.getBlock().setType(Material.AIR);
        }
        for (Player viewer : new ArrayList<>(instance.getInventory().getViewers().stream()
                .filter(h -> h instanceof Player).map(h -> (Player) h).toList())) {
            viewer.closeInventory();
        }
    }

    public void despawnAllOnDisable() {
        for (MystInstance instance : active) {
            HologramUtil.remove(instance.getHologram());
        }
        saveActiveMysts();
    }

    // ==================== ПОИСК ====================

    public MystInstance findByBlockLocation(Location loc) {
        for (MystInstance instance : active) {
            if (instance.getLocation().getWorld().equals(loc.getWorld())
                    && instance.getLocation().getBlockX() == loc.getBlockX()
                    && instance.getLocation().getBlockY() == loc.getBlockY()
                    && instance.getLocation().getBlockZ() == loc.getBlockZ()) {
                return instance;
            }
        }
        return null;
    }

    public MystInstance findByInventory(Inventory inv) {
        if (!(inv.getHolder() instanceof ru.myst.gui.MystInventoryHolder holder)) return null;
        return holder.getInstance();
    }

    public MystInstance findNearest(Location from) {
        MystInstance best = null;
        double bestDist = Double.MAX_VALUE;
        for (MystInstance instance : active) {
            if (!instance.getLocation().getWorld().equals(from.getWorld())) continue;
            double d = instance.getLocation().distanceSquared(from);
            if (d < bestDist) {
                bestDist = d;
                best = instance;
            }
        }
        return best;
    }

    public List<MystInstance> getActive() {
        return active;
    }

    public boolean isInsideAnyZone(Location loc) {
        for (MystInstance instance : active) {
            if (instance.getLocation().getWorld().equals(loc.getWorld())
                    && instance.getLocation().distance(loc) <= zoneRadius) {
                return true;
            }
        }
        return false;
    }

    public MystInstance getZoneAt(Location loc) {
        for (MystInstance instance : active) {
            if (instance.getLocation().getWorld().equals(loc.getWorld())
                    && instance.getLocation().distance(loc) <= zoneRadius) {
                return instance;
            }
        }
        return null;
    }

    public long getPickupCooldownMs() {
        return pickupCooldownMs;
    }

    public int getZoneRadius() {
        return zoneRadius;
    }

    public Random getRandom() {
        return random;
    }

    public MystPlugin getPlugin() {
        return plugin;
    }
}
