package com.adrian.myplot;

import org.bukkit.Material;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Plot {
    private final PlotId id;
    private final UUID owner;
    private final String ownerName;
    private final Set<UUID> trusted = new HashSet<>();
    private Material border;

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
    public Material getBorder() { return border; }
    public void setBorder(Material border) { this.border = border; }

    public boolean canBuild(UUID uuid) {
        return owner.equals(uuid) || trusted.contains(uuid);
    }
}
