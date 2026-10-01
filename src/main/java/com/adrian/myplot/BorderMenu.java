package com.adrian.myplot;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class BorderMenu implements InventoryHolder {
    private final PlotId plotId;
    private final List<Material> mats;
    private final Inventory inv;

    public BorderMenu(PlotId plotId, List<Material> mats) {
        this.plotId = plotId;
        this.mats = mats;
        int rows = Math.max(1, Math.min(6, (mats.size() + 8) / 9));
        this.inv = Bukkit.createInventory(this, rows * 9, Msg.parse("<gold><bold>Borde de tu parcela"));
        for (int i = 0; i < mats.size() && i < rows * 9; i++) {
            Material m = mats.get(i);
            String name = m.name().replace("_SLAB", "").replace('_', ' ').toLowerCase();
            ItemStack item = new ItemStack(m);
            item.editMeta(meta -> meta.displayName(Msg.parse("<yellow>" + name)));
            inv.setItem(i, item);
        }
    }

    public PlotId plotId() {
        return plotId;
    }

    public Material materialAt(int slot) {
        return slot >= 0 && slot < mats.size() ? mats.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
