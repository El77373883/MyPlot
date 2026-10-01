package com.adrian.myplot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class PlotWorld {
    public static final int ROAD = 8;               // calles de 8 bloques
    public static final int OFFSET = ROAD / 2;
    public static final int GROUND_Y = 64;
    public static final Material UNCLAIMED_BORDER = Material.RED_SANDSTONE_SLAB;

    private final String name;
    private final int size;
    private final int period;

    public PlotWorld(String name, int size) {
        this.name = name;
        this.size = size;
        this.period = size + ROAD;
    }

    public String name() { return name; }
    public int size() { return size; }
    public int period() { return period; }

    /** Parcela en esa coordenada, o null si es calle. */
    public PlotId plotAt(int x, int z) {
        int u = x - OFFSET;
        int v = z - OFFSET;
        int mx = Math.floorMod(u, period);
        int mz = Math.floorMod(v, period);
        if (mx >= size || mz >= size) return null;
        return new PlotId(name, Math.floorDiv(u, period), Math.floorDiv(v, period));
    }

    /** Las 4 parcelas del centro son el spawn y no se pueden reclamar. */
    public boolean isSpawnPlot(int px, int pz) {
        return (px == -1 || px == 0) && (pz == -1 || pz == 0);
    }

    public boolean inSpawnArea(int x, int z) {
        int lo = -size - OFFSET;
        int hi = size + OFFSET - 1;
        return x >= lo && x <= hi && z >= lo && z <= hi;
    }

    public int minX(int px) { return px * period + OFFSET; }
    public int maxX(int px) { return minX(px) + size - 1; }
    public int minZ(int pz) { return pz * period + OFFSET; }
    public int maxZ(int pz) { return minZ(pz) + size - 1; }

    public Location spawnLocation(World w) {
        return new Location(w, 0.5, GROUND_Y + 1, 6.5);
    }
}
