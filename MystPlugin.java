package ru.myst;

import org.bukkit.plugin.java.JavaPlugin;
import ru.myst.commands.MystCommand;
import ru.myst.listeners.MystListener;
import ru.myst.manager.MystManager;

public class MystPlugin extends JavaPlugin {

    private MystManager mystManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.mystManager = new MystManager(this);
        mystManager.loadAll();
        mystManager.startTicking();

        MystCommand command = new MystCommand(this, mystManager);
        getCommand("myst").setExecutor(command);
        getCommand("myst").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new MystListener(this, mystManager), this);

        getLogger().info("MystPlugin включен. Активных мистов: " + mystManager.getActive().size());
    }

    @Override
    public void onDisable() {
        if (mystManager != null) {
            mystManager.stopTicking();
            mystManager.despawnAllOnDisable();
        }
        getLogger().info("MystPlugin выключен.");
    }

    public MystManager getMystManager() {
        return mystManager;
    }
}
