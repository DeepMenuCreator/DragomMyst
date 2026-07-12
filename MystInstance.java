package ru.myst.model;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Конкретный заспавненный мист в мире: локация, состояние, инвентарь, голограмма.
 */
public class MystInstance {

    public enum State {
        WAITING_OPEN,   // таймер до открытия, открывать нельзя
        OPEN,           // активен, можно открывать и брать лут
        CLOSED          // закрылся, показывает только имя, открыть нельзя, скоро исчезнет
    }

    private final UUID uuid = UUID.randomUUID();
    private final MystType type;
    private final Location location;
    private State state = State.WAITING_OPEN;
    private long stateChangedAt = System.currentTimeMillis();
    private Inventory inventory;
    private TextDisplay hologram;
    // кулдаун забора предметов по игроку
    private final Map<UUID, Long> pickupCooldowns = new HashMap<>();

    public MystInstance(MystType type, Location location) {
        this.type = type;
        this.location = location;
    }

    public UUID getUuid() { return uuid; }
    public MystType getType() { return type; }
    public Location getLocation() { return location; }

    public State getState() { return state; }
    public void setState(State state) {
        this.state = state;
        this.stateChangedAt = System.currentTimeMillis();
    }

    /** Используется только при восстановлении из файла данных после рестарта сервера. */
    public void restoreState(State state, long changedAt) {
        this.state = state;
        this.stateChangedAt = changedAt;
    }

    public long getStateChangedAt() { return stateChangedAt; }

    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public TextDisplay getHologram() { return hologram; }
    public void setHologram(TextDisplay hologram) { this.hologram = hologram; }

    public boolean isOnPickupCooldown(UUID player, long cooldownMs) {
        Long last = pickupCooldowns.get(player);
        return last != null && (System.currentTimeMillis() - last) < cooldownMs;
    }

    public void setPickupCooldown(UUID player) {
        pickupCooldowns.put(player, System.currentTimeMillis());
    }

    public long secondsUntilOpen() {
        long elapsed = (System.currentTimeMillis() - stateChangedAt) / 1000L;
        return Math.max(0, type.getCountdownBeforeOpenSeconds() - elapsed);
    }

    public long secondsUntilClose() {
        long elapsed = (System.currentTimeMillis() - stateChangedAt) / 1000L;
        return Math.max(0, type.getOpenDurationSeconds() - elapsed);
    }
}
