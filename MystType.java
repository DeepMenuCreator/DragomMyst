package ru.myst.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Шаблон миста: имя, голограммы, тайминги, лут, флаги security/kd/use.
 * Создаётся один раз командой /myst create <имя> и переиспользуется при каждом спавне.
 */
public class MystType {

    private final String id;
    private String displayName;
    private String hologramFormat;
    private String hologramOpeningFormat;
    private String hologramOpenFormat;
    private int countdownBeforeOpenSeconds;
    private int openDurationSeconds;
    private boolean security;
    private boolean kd;
    private boolean use;
    private Material treasureItem;
    private String treasureName;
    private final List<LootEntry> loot = new ArrayList<>();

    public MystType(String id) {
        this.id = id;
        this.displayName = "&a" + id;
        this.hologramFormat = "&e&l{name}";
        this.hologramOpeningFormat = "&7Откроется через: &f{time}";
        this.hologramOpenFormat = "&aЗакроется через: &f{time}";
        this.countdownBeforeOpenSeconds = 15;
        this.openDurationSeconds = 300;
        this.security = true;
        this.kd = true;
        this.use = false;
        this.treasureItem = Material.PHANTOM_MEMBRANE;
        this.treasureName = "&6Сокровище";
    }

    public ItemStackRoll rollLoot(Random random) {
        int totalWeight = loot.stream().mapToInt(LootEntry::getWeight).sum();
        if (totalWeight <= 0 || loot.isEmpty()) return null;
        int roll = random.nextInt(totalWeight);
        int acc = 0;
        for (LootEntry entry : loot) {
            acc += entry.getWeight();
            if (roll < acc) {
                return new ItemStackRoll(entry.roll(random));
            }
        }
        return new ItemStackRoll(loot.get(loot.size() - 1).roll(random));
    }

    public record ItemStackRoll(org.bukkit.inventory.ItemStack item) {}

    // ----- getters / setters -----

    public String getId() { return id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getHologramFormat() { return hologramFormat; }
    public void setHologramFormat(String hologramFormat) { this.hologramFormat = hologramFormat; }

    public String getHologramOpeningFormat() { return hologramOpeningFormat; }
    public void setHologramOpeningFormat(String v) { this.hologramOpeningFormat = v; }

    public String getHologramOpenFormat() { return hologramOpenFormat; }
    public void setHologramOpenFormat(String v) { this.hologramOpenFormat = v; }

    public int getCountdownBeforeOpenSeconds() { return countdownBeforeOpenSeconds; }
    public void setCountdownBeforeOpenSeconds(int v) { this.countdownBeforeOpenSeconds = v; }

    public int getOpenDurationSeconds() { return openDurationSeconds; }
    public void setOpenDurationSeconds(int v) { this.openDurationSeconds = v; }

    public boolean isSecurity() { return security; }
    public void setSecurity(boolean security) { this.security = security; }

    public boolean isKd() { return kd; }
    public void setKd(boolean kd) { this.kd = kd; }

    public boolean isUse() { return use; }
    public void setUse(boolean use) { this.use = use; }

    public Material getTreasureItem() { return treasureItem; }
    public void setTreasureItem(Material treasureItem) { this.treasureItem = treasureItem; }

    public String getTreasureName() { return treasureName; }
    public void setTreasureName(String treasureName) { this.treasureName = treasureName; }

    public List<LootEntry> getLoot() { return loot; }
}
