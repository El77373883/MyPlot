package com.adrian.myplot;

public record PlotId(String world, int x, int z) {
    public String key() {
        return world + ";" + x + ";" + z;
    }
}
