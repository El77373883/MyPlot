package com.adrian.myplot;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class MyPlot extends JavaPlugin {

    private PlotManager manager;
    private EconomyHook economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new PlotManager(this);
        manager.loadConfigValues();
        manager.load();

        getServer().getPluginManager().registerEvents(new PlotListener(this), this);

        PlotCommand plot = new PlotCommand(this);
        PluginCommand c1 = Objects.requireNonNull(getCommand("plot"));
        c1.setExecutor(plot);
        c1.setTabCompleter(plot);

        PlotAdminCommand admin = new PlotAdminCommand(this);
        PluginCommand c2 = Objects.requireNonNull(getCommand("plotadmin"));
        c2.setExecutor(admin);
        c2.setTabCompleter(admin);

        // Los mundos se cargan cuando el servidor ya arranco
        getServer().getScheduler().runTask(this, () -> manager.loadWorlds());

        getLogger().info("MyPlot v1.0 activado - plugin por soyadrianyt001");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    public PlotManager manager() {
        return manager;
    }

    /** Devuelve la economia de Vault, o null si no hay. */
    public EconomyHook economy() {
        if (economy == null && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            EconomyHook hook = new EconomyHook();
            if (hook.setup()) economy = hook;
        }
        return economy;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        int size = 35;
        try {
            if (id != null && !id.isEmpty()) size = Integer.parseInt(id);
        } catch (NumberFormatException ignored) {
        }
        return new PlotGenerator(size);
    }
}
