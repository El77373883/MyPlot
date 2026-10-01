package com.adrian.myplot;

import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlotListener implements Listener {
    private final MyPlot plugin;
    private final PlotManager pm;
    private final Map<UUID, String> last = new HashMap<>();

    public PlotListener(MyPlot plugin) {
        this.plugin = plugin;
        this.pm = plugin.manager();
    }

    private void deny(Player p) {
        p.sendActionBar(Msg.parse("<red>No puedes construir aqui."));
    }

    // ---------- Proteccion de bloques ----------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!pm.canBuild(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!pm.canBuild(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || pm.getPlotWorld(b.getWorld()) == null) return;
        Player p = e.getPlayer();
        if (pm.canBuild(p, b)) return;

        boolean risky = b.getState() instanceof Container;
        ItemStack hand = e.getItem();
        if (!risky && hand != null) {
            Material t = hand.getType();
            String n = t.name();
            risky = n.endsWith("_BUCKET") || n.endsWith("_SPAWN_EGG")
                    || t == Material.FLINT_AND_STEEL || t == Material.FIRE_CHARGE
                    || t == Material.ARMOR_STAND || t == Material.END_CRYSTAL;
        }
        if (risky) {
            e.setCancelled(true);
            deny(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!pm.canBuild(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!pm.canBuild(e.getPlayer(), e.getBlockClicked())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof ArmorStand) && !(e.getEntity() instanceof Hanging)) return;
        if (!pm.canBuild(p, e.getEntity().getLocation().getBlock())) {
            e.setCancelled(true);
            deny(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!(e.getRemover() instanceof Player p)) return;
        if (!pm.canBuild(p, e.getEntity().getLocation().getBlock())) {
            e.setCancelled(true);
            deny(p);
        }
    }

    // ---------- Explosiones, fuego, liquidos, pistones ----------

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (pm.getPlotWorld(e.getLocation().getWorld()) != null) e.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (pm.getPlotWorld(e.getBlock().getWorld()) != null) e.blockList().clear();
    }

    @EventHandler
    public void onBurn(BlockBurnEvent e) {
        if (pm.getPlotWorld(e.getBlock().getWorld()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent e) {
        if (pm.getPlotWorld(e.getBlock().getWorld()) == null) return;
        Player p = e.getPlayer();
        if (p == null || !pm.canBuild(p, e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (pm.getPlotWorld(e.getBlock().getWorld()) == null) return;
        if (!Objects.equals(pm.groupRoot(e.getBlock()), pm.groupRoot(e.getToBlock()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!pistonSafe(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!pistonSafe(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    private boolean pistonSafe(Block piston, List<Block> moved, BlockFace dir) {
        if (pm.getPlotWorld(piston.getWorld()) == null) return true;
        PlotId home = pm.groupRoot(piston);
        if (!Objects.equals(pm.groupRoot(piston.getRelative(dir)), home)) return false;
        for (Block b : moved) {
            if (!Objects.equals(pm.groupRoot(b), home)) return false;
            if (!Objects.equals(pm.groupRoot(b.getRelative(dir)), home)) return false;
            if (!Objects.equals(pm.groupRoot(b.getRelative(dir.getOppositeFace())), home)) return false;
        }
        return true;
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (plugin.getConfig().getBoolean("mobs-naturales", false)) return;
        if (pm.getPlotWorld(e.getLocation().getWorld()) != null) e.setCancelled(true);
    }

    // ---------- Aviso al entrar a una parcela ----------

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom();
        Location t = e.getTo();
        if (t == null) return;
        if (f.getBlockX() == t.getBlockX() && f.getBlockZ() == t.getBlockZ()
                && f.getWorld() == t.getWorld()) return;

        Player p = e.getPlayer();
        PlotWorld pw = pm.getPlotWorld(t.getWorld());
        String key;
        PlotId id = null;
        if (pw == null) {
            key = "";
        } else if (pw.inSpawnArea(t.getBlockX(), t.getBlockZ())) {
            key = "spawn";
        } else {
            id = pm.rootAt(pw, t.getBlockX(), t.getBlockZ());
            key = id == null ? "road" : id.key();
        }

        String old = last.put(p.getUniqueId(), key);
        if (key.equals(old) || key.isEmpty() || key.equals("road")) return;

        Plot plot = null;
        String path;
        double shown = plugin.getConfig().getDouble("precio", 8000);
        if (key.equals("spawn")) {
            path = "spawn";
        } else {
            plot = pm.getPlot(id);
            if (plot == null) {
                path = "libre";
            } else if (plot.getSalePrice() > 0) {
                path = "venta";
                shown = plot.getSalePrice();
            } else {
                path = "ocupada";
            }
        }
        String owner = plot != null ? plot.getOwnerName() : "";
        String price = String.format(Locale.US, "%,.0f", shown);
        String title = fill(plugin.getConfig().getString("mensajes." + path + ".titulo", ""), owner, price);
        String sub = fill(plugin.getConfig().getString("mensajes." + path + ".subtitulo", ""), owner, price);

        p.showTitle(Title.title(Msg.parse(title), Msg.parse(sub),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(1800), Duration.ofMillis(400))));
    }

    private String fill(String s, String owner, String price) {
        return s.replace("{jugador}", owner).replace("{precio}", price);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
    }

    // ---------- Menu de bordes ----------

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof BorderMenu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        Material m = menu.materialAt(e.getSlot());
        if (m == null) return;
        Player p = (Player) e.getWhoClicked();
        Plot plot = pm.getPlot(menu.plotId());
        if (plot == null || (!plot.getOwner().equals(p.getUniqueId()) && !p.hasPermission("myplot.admin"))) {
            p.closeInventory();
            return;
        }
        pm.setBorder(plot, m);
        p.closeInventory();
        Msg.send(p, "<green>Borde cambiado.");
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof BorderMenu) e.setCancelled(true);
    }
}
