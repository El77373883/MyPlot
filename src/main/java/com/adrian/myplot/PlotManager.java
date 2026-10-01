package com.adrian.myplot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlotManager {
    private final MyPlot plugin;
    private final File file;
    private final Map<String, PlotWorld> worlds = new LinkedHashMap<>();
    private final Map<String, Integer> savedSizes = new LinkedHashMap<>();
    private final Map<String, Plot> plots = new HashMap<>();
    private final Set<String> clearing = new HashSet<>();
    private final List<Material> allowedBorders = new ArrayList<>();

    public PlotManager(MyPlot plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ---------- Config ----------

    public void loadConfigValues() {
        allowedBorders.clear();
        for (String s : plugin.getConfig().getStringList("bordes-permitidos")) {
            Material m = Material.matchMaterial(s);
            if (m != null && m.name().endsWith("_SLAB")) allowedBorders.add(m);
        }
    }

    public List<Material> allowedBorders() {
        return allowedBorders;
    }

    public Material claimedBorder() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("borde-reclamado", "QUARTZ_SLAB"));
        return m != null ? m : Material.QUARTZ_SLAB;
    }

    // ---------- Datos ----------

    public void load() {
        savedSizes.clear();
        plots.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection ws = y.getConfigurationSection("worlds");
        if (ws != null) {
            for (String n : ws.getKeys(false)) savedSizes.put(n, ws.getInt(n));
        }

        ConfigurationSection ps = y.getConfigurationSection("plots");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(key);
                String[] parts = key.split(";");
                if (s == null || parts.length != 3) continue;
                try {
                    PlotId id = new PlotId(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    UUID owner = UUID.fromString(s.getString("owner", ""));
                    Material border = Material.matchMaterial(s.getString("border", ""));
                    Plot plot = new Plot(id, owner, s.getString("name", "?"),
                            border != null ? border : claimedBorder());
                    for (String t : s.getStringList("trusted")) plot.getTrusted().add(UUID.fromString(t));
                    plots.put(key, plot);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Parcela invalida en data.yml: " + key);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Integer> e : savedSizes.entrySet()) {
            y.set("worlds." + e.getKey(), e.getValue());
        }
        for (Plot p : plots.values()) {
            String b = "plots." + p.getId().key();
            y.set(b + ".owner", p.getOwner().toString());
            y.set(b + ".name", p.getOwnerName());
            List<String> trusted = new ArrayList<>();
            for (UUID u : p.getTrusted()) trusted.add(u.toString());
            y.set(b + ".trusted", trusted);
            y.set(b + ".border", p.getBorder().name());
        }
        plugin.getDataFolder().mkdirs();
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar data.yml: " + ex.getMessage());
        }
    }

    // ---------- Mundos ----------

    public void loadWorlds() {
        for (Map.Entry<String, Integer> e : savedSizes.entrySet()) {
            if (worlds.containsKey(e.getKey())) continue;
            if (buildWorld(e.getKey(), e.getValue()) == null) {
                plugin.getLogger().warning("No se pudo cargar el mundo " + e.getKey());
            }
        }
    }

    private World buildWorld(String name, int size) {
        WorldCreator wc = new WorldCreator(name).generator(new PlotGenerator(size)).generateStructures(false);
        World w = wc.createWorld();
        if (w == null) return null;
        PlotWorld pw = new PlotWorld(name, size);
        worlds.put(name, pw);
        w.setSpawnLocation(pw.spawnLocation(w));
        return w;
    }

    public World createPlotWorld(String name, int size) {
        World w = buildWorld(name, size);
        if (w != null) {
            savedSizes.put(name, size);
            save();
        }
        return w;
    }

    public boolean deletePlotWorld(String name) {
        PlotWorld pw = worlds.remove(name);
        savedSizes.remove(name);
        World w = Bukkit.getWorld(name);
        if (w != null) {
            World fallback = Bukkit.getWorlds().get(0);
            for (Player p : new ArrayList<>(w.getPlayers())) {
                p.teleport(fallback.getSpawnLocation());
            }
            Bukkit.unloadWorld(w, true);
        }
        plots.keySet().removeIf(k -> k.startsWith(name + ";"));
        save();
        return pw != null;
    }

    public Collection<PlotWorld> allWorlds() {
        return worlds.values();
    }

    public PlotWorld getPlotWorld(World w) {
        return w == null ? null : worlds.get(w.getName());
    }

    public PlotWorld getPlotWorld(String name) {
        return worlds.get(name);
    }

    // ---------- Parcelas ----------

    public PlotId plotAt(Location l) {
        PlotWorld pw = getPlotWorld(l.getWorld());
        return pw == null ? null : pw.plotAt(l.getBlockX(), l.getBlockZ());
    }

    public PlotId plotAt(Block b) {
        PlotWorld pw = getPlotWorld(b.getWorld());
        return pw == null ? null : pw.plotAt(b.getX(), b.getZ());
    }

    public Plot getPlot(PlotId id) {
        return id == null ? null : plots.get(id.key());
    }

    public boolean isClearing(PlotId id) {
        return clearing.contains(id.key());
    }

    public List<Plot> plotsOf(UUID owner) {
        List<Plot> list = new ArrayList<>();
        for (Plot p : plots.values()) {
            if (p.getOwner().equals(owner)) list.add(p);
        }
        list.sort(Comparator.comparing((Plot p) -> p.getId().world())
                .thenComparingInt(p -> p.getId().x())
                .thenComparingInt(p -> p.getId().z()));
        return list;
    }

    public int countOf(UUID owner) {
        return plotsOf(owner).size();
    }

    public boolean canBuild(Player p, Block b) {
        PlotWorld pw = getPlotWorld(b.getWorld());
        if (pw == null) return true;
        if (p.hasPermission("myplot.admin")) return true;
        PlotId id = pw.plotAt(b.getX(), b.getZ());
        if (id == null || pw.isSpawnPlot(id.x(), id.z())) return false;
        Plot plot = getPlot(id);
        return plot != null && plot.canBuild(p.getUniqueId());
    }

    public void claim(Plot plot) {
        plots.put(plot.getId().key(), plot);
        save();
        applyBorder(plot.getId(), plot.getBorder());
    }

    public void setBorder(Plot plot, Material mat) {
        plot.setBorder(mat);
        save();
        applyBorder(plot.getId(), mat);
    }

    public Location home(Plot plot) {
        PlotId id = plot.getId();
        PlotWorld pw = worlds.get(id.world());
        World w = Bukkit.getWorld(id.world());
        if (pw == null || w == null) return null;
        int cx = pw.minX(id.x()) + pw.size() / 2;
        int cz = pw.minZ(id.z()) + pw.size() / 2;
        int y = w.getHighestBlockYAt(cx, cz) + 1;
        return new Location(w, cx + 0.5, y, cz + 0.5);
    }

    // ---------- Bloques ----------

    public void applyBorder(PlotId id, Material mat) {
        PlotWorld pw = worlds.get(id.world());
        World w = Bukkit.getWorld(id.world());
        if (pw == null || w == null) return;
        int x0 = pw.minX(id.x());
        int z0 = pw.minZ(id.z());
        int x1 = pw.maxX(id.x());
        int z1 = pw.maxZ(id.z());
        int y = PlotWorld.GROUND_Y + 1;
        for (int i = 0; i < pw.size(); i++) {
            set(w, x0 + i, y, z0, mat);
            set(w, x0 + i, y, z1, mat);
            set(w, x0, y, z0 + i, mat);
            set(w, x1, y, z0 + i, mat);
        }
    }

    /** Quita al dueño y deja la parcela limpia (lo hace poco a poco para no dar lag). */
    public void removeAndClear(PlotId id, Runnable done) {
        plots.remove(id.key());
        save();
        PlotWorld pw = worlds.get(id.world());
        World w = Bukkit.getWorld(id.world());
        if (pw == null || w == null) {
            if (done != null) done.run();
            return;
        }
        clearing.add(id.key());
        final int minX = pw.minX(id.x());
        final int minZ = pw.minZ(id.z());
        final int size = pw.size();
        final int minY = w.getMinHeight();
        final int maxY = w.getMaxHeight();
        new BukkitRunnable() {
            int col = 0;

            @Override
            public void run() {
                int end = Math.min(col + 35, size * size);
                for (; col < end; col++) {
                    resetColumn(w, minX + col % size, minZ + col / size, minY, maxY);
                }
                if (col >= size * size) {
                    cancel();
                    clearing.remove(id.key());
                    applyBorder(id, PlotWorld.UNCLAIMED_BORDER);
                    if (done != null) done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void resetColumn(World w, int x, int z, int minY, int maxY) {
        int g = PlotWorld.GROUND_Y;
        for (int y = maxY - 1; y > g; y--) set(w, x, y, z, Material.AIR);
        set(w, x, g, z, Material.GRASS_BLOCK);
        for (int y = g - 4; y < g; y++) set(w, x, y, z, Material.DIRT);
        for (int y = minY + 1; y < g - 4; y++) set(w, x, y, z, Material.STONE);
        set(w, x, minY, z, Material.BEDROCK);
    }

    private void set(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() != m) b.setType(m, false);
    }
}
