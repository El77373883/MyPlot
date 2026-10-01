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
    private int limit; // radio del mundo en bloques (0 = sin limite)

    public PlotWorld(String name, int size) {
        this(name, size, 0);
    }

    public PlotWorld(String name, int size, int limit) {
        this.name = name;
        this.size = size;
        this.period = size + ROAD;
        this.limit = limit;
    }

    public String name() { return name; }
    public int size() { return size; }
    public int period() { return period; }
    public int limit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }

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

    /** Material de la calle en esa coordenada, o null si es parcela o spawn. */
    public Material roadMaterial(int x, int z) {
        if (inSpawnArea(x, z)) return null;
        int mx = Math.floorMod(x - OFFSET, period);
        int mz = Math.floorMod(z - OFFSET, period);
        boolean roadX = mx >= size;
        boolean roadZ = mz >= size;
        if (!roadX && !roadZ) return null;
        boolean sidewalk = (roadX && !roadZ && (mx == size || mx == period - 1))
                || (roadZ && !roadX && (mz == size || mz == period - 1));
        return sidewalk ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE;
    }

    public int minX(int px) { return px * period + OFFSET; }
    public int maxX(int px) { return minX(px) + size - 1; }
    public int minZ(int pz) { return pz * period + OFFSET; }
    public int maxZ(int pz) { return minZ(pz) + size - 1; }

    public Location spawnLocation(World w) {
        return new Location(w, 0.5, GROUND_Y + 1, 6.5);
    }
}
