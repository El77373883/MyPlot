package com.adrian.myplot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class PlotManager {
    private final MyPlot plugin;
    private final File file;
    private final Map<String, PlotWorld> worlds = new LinkedHashMap<>();
    private final Map<String, Integer> savedSizes = new LinkedHashMap<>();
    private final Map<String, Integer> savedLimits = new LinkedHashMap<>();
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

    private boolean isBorderMaterial(Material m) {
        return m == PlotWorld.UNCLAIMED_BORDER || m == claimedBorder() || allowedBorders.contains(m);
    }

    // ---------- Datos ----------

    public void load() {
        savedSizes.clear();
        savedLimits.clear();
        plots.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection ws = y.getConfigurationSection("worlds");
        if (ws != null) {
            for (String n : ws.getKeys(false)) {
                if (ws.isConfigurationSection(n)) {
                    savedSizes.put(n, ws.getInt(n + ".size"));
                    savedLimits.put(n, ws.getInt(n + ".limit", 0));
                } else {
                    savedSizes.put(n, ws.getInt(n));
                    savedLimits.put(n, 0);
                }
            }
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
                    plot.setSalePrice(s.getDouble("sale", 0));
                    for (String f : s.getStringList("merged")) {
                        try {
                            plot.getMerged().add(BlockFace.valueOf(f));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
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
            y.set("worlds." + e.getKey() + ".size", e.getValue());
            y.set("worlds." + e.getKey() + ".limit", savedLimits.getOrDefault(e.getKey(), 0));
        }
        for (Plot p : plots.values()) {
            String b = "plots." + p.getId().key();
            y.set(b + ".owner", p.getOwner().toString());
            y.set(b + ".name", p.getOwnerName());
            List<String> trusted = new ArrayList<>();
            for (UUID u : p.getTrusted()) trusted.add(u.toString());
            y.set(b + ".trusted", trusted);
            y.set(b + ".border", p.getBorder().name());
            y.set(b + ".sale", p.getSalePrice());
            List<String> merged = new ArrayList<>();
            for (BlockFace f : p.getMerged()) merged.add(f.name());
            y.set(b + ".merged", merged);
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
            if (buildWorld(e.getKey(), e.getValue(), savedLimits.getOrDefault(e.getKey(), 0)) == null) {
                plugin.getLogger().warning("No se pudo cargar el mundo " + e.getKey());
            }
        }
    }

    private World buildWorld(String name, int size, int limit) {
        WorldCreator wc = new WorldCreator(name).generator(new PlotGenerator(size)).generateStructures(false);
        World w = wc.createWorld();
        if (w == null) return null;
        PlotWorld pw = new PlotWorld(name, size, limit);
        worlds.put(name, pw);
        w.setSpawnLocation(pw.spawnLocation(w));
        applyLimit(w, limit);
        return w;
    }

    private void applyLimit(World w, int limit) {
        WorldBorder wb = w.getWorldBorder();
        wb.setCenter(0, 0);
        wb.setSize(limit > 0 ? limit * 2.0 : 5.9999968E7);
    }

    public World createPlotWorld(String name, int size, int limit) {
        World w = buildWorld(name, size, limit);
        if (w != null) {
            savedSizes.put(name, size);
            savedLimits.put(name, limit);
            save();
        }
        return w;
    }

    public boolean setWorldLimit(String name, int limit) {
        PlotWorld pw = worlds.get(name);
        World w = Bukkit.getWorld(name);
        if (pw == null || w == null) return false;
        pw.setLimit(limit);
        savedLimits.put(name, limit);
        applyLimit(w, limit);
        save();
        return true;
    }

    public boolean deletePlotWorld(String name) {
        PlotWorld pw = worlds.remove(name);
        savedSizes.remove(name);
        savedLimits.remove(name);
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

    // ---------- Busqueda de parcelas ----------

    /** Parcela exacta en esa posicion (null si es calle). */
    public PlotId plotAt(Location l) {
        PlotWorld pw = getPlotWorld(l.getWorld());
        return pw == null ? null : pw.plotAt(l.getBlockX(), l.getBlockZ());
    }

    public Plot getPlot(PlotId id) {
        return id == null ? null : plots.get(id.key());
    }

    public boolean isClearing(PlotId id) {
        return clearing.contains(id.key());
    }

    /** Como plotAt, pero las calles entre parcelas fusionadas cuentan como parte de la parcela. */
    private PlotId memberAt(PlotWorld pw, int x, int z) {
        PlotId id = pw.plotAt(x, z);
        if (id != null) return id;
        if (pw.inSpawnArea(x, z)) return null;
        int period = pw.period();
        int size = pw.size();
        int u = x - PlotWorld.OFFSET;
        int v = z - PlotWorld.OFFSET;
        int px = Math.floorDiv(u, period);
        int pz = Math.floorDiv(v, period);
        boolean roadX = Math.floorMod(u, period) >= size;
        boolean roadZ = Math.floorMod(v, period) >= size;
        String w = pw.name();
        Plot a = plots.get(new PlotId(w, px, pz).key());
        if (a == null) return null;
        if (roadX && !roadZ) {
            return a.getMerged().contains(BlockFace.EAST) ? a.getId() : null;
        }
        if (roadZ && !roadX) {
            return a.getMerged().contains(BlockFace.SOUTH) ? a.getId() : null;
        }
        Plot b = plots.get(new PlotId(w, px + 1, pz).key());
        Plot c = plots.get(new PlotId(w, px, pz + 1).key());
        if (b != null && c != null
                && a.getMerged().contains(BlockFace.EAST) && a.getMerged().contains(BlockFace.SOUTH)
                && b.getMerged().contains(BlockFace.SOUTH) && c.getMerged().contains(BlockFace.EAST)) {
            return a.getId();
        }
        return null;
    }

    public static PlotId neighbor(PlotId id, BlockFace f) {
        return switch (f) {
            case NORTH -> new PlotId(id.world(), id.x(), id.z() - 1);
            case SOUTH -> new PlotId(id.world(), id.x(), id.z() + 1);
            case EAST -> new PlotId(id.world(), id.x() + 1, id.z());
            case WEST -> new PlotId(id.world(), id.x() - 1, id.z());
            default -> null;
        };
    }

    /** Todas las parcelas fusionadas con esta (incluida ella). */
    public Set<PlotId> connected(PlotId start) {
        Set<PlotId> seen = new LinkedHashSet<>();
        Deque<PlotId> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            PlotId cur = queue.poll();
            Plot pl = plots.get(cur.key());
            if (pl == null) continue;
            for (BlockFace f : pl.getMerged()) {
                PlotId n = neighbor(cur, f);
                if (n != null && seen.add(n)) queue.add(n);
            }
        }
        return seen;
    }

    public PlotId root(PlotId id) {
        PlotId best = id;
        for (PlotId c : connected(id)) {
            if (c.x() < best.x() || (c.x() == best.x() && c.z() < best.z())) best = c;
        }
        return best;
    }

    /** Identificador del grupo (parcelas fusionadas comparten el mismo). */
    public PlotId rootAt(PlotWorld pw, int x, int z) {
        PlotId id = memberAt(pw, x, z);
        return id == null ? null : root(id);
    }

    public PlotId groupRoot(Block b) {
        PlotWorld pw = getPlotWorld(b.getWorld());
        return pw == null ? null : rootAt(pw, b.getX(), b.getZ());
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
        PlotId id = memberAt(pw, b.getX(), b.getZ());
        if (id == null || pw.isSpawnPlot(id.x(), id.z())) return false;
        Plot plot = getPlot(id);
        return plot != null && plot.canBuild(p.getUniqueId());
    }

    // ---------- Reclamar, vender, transferir ----------

    public void claim(Plot plot) {
        plots.put(plot.getId().key(), plot);
        save();
        applyBorder(plot.getId(), plot.getBorder());
        updateSign(plot);
    }

    public void setSale(Plot plot, double price) {
        plot.setSalePrice(price);
        save();
        updateSign(plot);
    }

    public void transfer(Plot plot, UUID newOwner, String newName) {
        plot.setOwner(newOwner, newName);
        plot.getTrusted().clear();
        plot.setSalePrice(0);
        save();
        updateSign(plot);
    }

    public void setBorder(Plot plot, Material mat) {
        for (PlotId id : connected(plot.getId())) {
            Plot p = getPlot(id);
            if (p != null) p.setBorder(mat);
        }
        save();
        refreshBorders(plot.getId());
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

    // ---------- Letrero ----------

    public void updateSign(Plot plot) {
        PlotId id = plot.getId();
        PlotWorld pw = worlds.get(id.world());
        World w = Bukkit.getWorld(id.world());
        if (pw == null || w == null) return;
        Block b = w.getBlockAt(pw.minX(id.x()) + 1, PlotWorld.GROUND_Y + 1, pw.minZ(id.z()) + 1);
        if (b.getType() != Material.OAK_SIGN) {
            b.setType(Material.OAK_SIGN, false);
            BlockData data = b.getBlockData();
            if (data instanceof Rotatable rot) {
                rot.setRotation(BlockFace.NORTH_WEST);
                b.setBlockData(rot, false);
            }
        }
        if (b.getState() instanceof Sign sign) {
            SignSide side = sign.getSide(Side.FRONT);
            boolean sale = plot.getSalePrice() > 0;
            side.line(0, Msg.parse("<dark_blue><bold>[Parcela]"));
            side.line(1, Msg.parse("<black>" + plot.getOwnerName()));
            side.line(2, Msg.parse(sale ? "<dark_red><bold>EN VENTA"
                    : "<dark_gray>" + id.x() + ", " + id.z()));
            side.line(3, Msg.parse(sale
                    ? "<dark_green>$" + String.format(Locale.US, "%,.0f", plot.getSalePrice()) : ""));
            sign.setWaxed(true);
            sign.update(true, false);
        }
    }

    // ---------- Fusion ----------

    /** Devuelve null si todo salio bien, o el mensaje de error. */
    public String merge(Plot a, BlockFace face) {
        PlotWorld pw = worlds.get(a.getId().world());
        PlotId nid = neighbor(a.getId(), face);
        if (pw == null || nid == null) return "Direccion invalida.";
        if (pw.isSpawnPlot(nid.x(), nid.z())) return "No puedes fusionar con el spawn.";
        Plot b = getPlot(nid);
        if (b == null) return "La parcela vecina no tiene dueño.";
        if (!b.getOwner().equals(a.getOwner())) return "La parcela vecina no es del mismo dueño.";
        if (a.getMerged().contains(face)) return "Ya estan fusionadas.";
        if (a.getSalePrice() > 0 || b.getSalePrice() > 0) return "Una de las parcelas esta en venta.";
        if (isClearing(a.getId()) || isClearing(nid)) return "Una parcela se esta limpiando, espera unos segundos.";

        Set<PlotId> union = new LinkedHashSet<>(connected(a.getId()));
        union.addAll(connected(nid));
        int max = plugin.getConfig().getInt("max-fusion", 4);
        if (union.size() > max) return "El maximo es de " + max + " parcelas fusionadas.";

        a.getMerged().add(face);
        b.getMerged().add(face.getOppositeFace());

        // Todo el grupo comparte accesos y borde
        Set<UUID> allTrusted = new HashSet<>();
        for (PlotId id : union) {
            Plot p = getPlot(id);
            if (p != null) allTrusted.addAll(p.getTrusted());
        }
        Material border = a.getBorder();
        for (PlotId id : union) {
            Plot p = getPlot(id);
            if (p == null) continue;
            p.getTrusted().addAll(allTrusted);
            p.setBorder(border);
        }
        save();
        refreshBorders(a.getId());
        return null;
    }

    public String unmerge(Plot a, BlockFace face) {
        if (!a.getMerged().contains(face)) return "No estan fusionadas en esa direccion.";
        PlotId nid = neighbor(a.getId(), face);
        Plot b = nid == null ? null : getPlot(nid);
        Set<PlotId> before = connected(a.getId());
        a.getMerged().remove(face);
        if (b != null) b.getMerged().remove(face.getOppositeFace());
        save();
        cleanupAfterSplit(before);
        return null;
    }

    private int[] bounds(PlotWorld pw, Set<PlotId> group) {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (PlotId id : group) {
            x0 = Math.min(x0, pw.minX(id.x()));
            z0 = Math.min(z0, pw.minZ(id.z()));
            x1 = Math.max(x1, pw.maxX(id.x()));
            z1 = Math.max(z1, pw.maxZ(id.z()));
        }
        return new int[]{x0, z0, x1, z1};
    }

    /** Despues de separar: devuelve las calles a su lugar y rehace los bordes. */
    private void cleanupAfterSplit(Set<PlotId> oldGroup) {
        PlotId any = oldGroup.iterator().next();
        PlotWorld pw = worlds.get(any.world());
        World w = Bukkit.getWorld(any.world());
        if (pw == null || w == null) return;
        int[] bb = bounds(pw, oldGroup);
        int y = PlotWorld.GROUND_Y;
        for (int x = bb[0] - 1; x <= bb[2] + 1; x++) {
            for (int z = bb[1] - 1; z <= bb[3] + 1; z++) {
                Material road = pw.roadMaterial(x, z);
                if (road == null || memberAt(pw, x, z) != null) continue;
                set(w, x, y, z, road);
                Block above = w.getBlockAt(x, y + 1, z);
                if (isBorderMaterial(above.getType())) above.setType(Material.AIR, false);
            }
        }
        Set<PlotId> done = new HashSet<>();
        for (PlotId id : oldGroup) {
            if (!plots.containsKey(id.key()) || done.contains(id)) continue;
            done.addAll(connected(id));
            refreshBorders(id);
        }
    }

    private boolean inGroup(PlotWorld pw, Set<PlotId> group, int x, int z) {
        PlotId id = memberAt(pw, x, z);
        return id != null && group.contains(id);
    }

    /** Rehace el borde por fuera de todo el grupo (y quita los bordes interiores). */
    public void refreshBorders(PlotId any) {
        Plot rootPlot = getPlot(root(any));
        PlotWorld pw = worlds.get(any.world());
        World w = Bukkit.getWorld(any.world());
        if (rootPlot == null || pw == null || w == null) return;
        Set<PlotId> group = connected(any);
        int[] bb = bounds(pw, group);
        Material border = rootPlot.getBorder();
        int y = PlotWorld.GROUND_Y + 1;
        for (int x = bb[0] - 1; x <= bb[2] + 1; x++) {
            for (int z = bb[1] - 1; z <= bb[3] + 1; z++) {
                if (!inGroup(pw, group, x, z)) continue;
                boolean edge = !inGroup(pw, group, x + 1, z) || !inGroup(pw, group, x - 1, z)
                        || !inGroup(pw, group, x, z + 1) || !inGroup(pw, group, x, z - 1);
                if (pw.plotAt(x, z) == null) set(w, x, PlotWorld.GROUND_Y, z, Material.GRASS_BLOCK);
                if (edge) {
                    set(w, x, y, z, border);
                } else {
                    Block b = w.getBlockAt(x, y, z);
                    if (isBorderMaterial(b.getType())) b.setType(Material.AIR, false);
                }
            }
        }
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
        Plot old = plots.get(id.key());
        Set<PlotId> oldGroup = connected(id);
        if (old != null) {
            for (BlockFace f : new ArrayList<>(old.getMerged())) {
                PlotId nid = neighbor(id, f);
                Plot n = nid == null ? null : plots.get(nid.key());
                if (n != null) n.getMerged().remove(f.getOppositeFace());
            }
            old.getMerged().clear();
        }
        plots.remove(id.key());
        save();
        if (oldGroup.size() > 1) cleanupAfterSplit(oldGroup);

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
