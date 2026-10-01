package com.adrian.myplot;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Plot {
    private final PlotId id;
    private UUID owner;
    private String ownerName;
    private final Set<UUID> trusted = new HashSet<>();
    private final Set<BlockFace> merged = EnumSet.noneOf(BlockFace.class);
    private Material border;
    private double salePrice;

    public Plot(PlotId id, UUID owner, String ownerName, Material border) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.border = border;
    }

    public PlotId getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }
    public Set<UUID> getTrusted() { return trusted; }
    public Set<BlockFace> getMerged() { return merged; }
    public Material getBorder() { return border; }
    public void setBorder(Material border) { this.border = border; }
    public double getSalePrice() { return salePrice; }
    public void setSalePrice(double salePrice) { this.salePrice = salePrice; }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public boolean canBuild(UUID uuid) {
        return owner.equals(uuid) || trusted.contains(uuid);
    }
}
