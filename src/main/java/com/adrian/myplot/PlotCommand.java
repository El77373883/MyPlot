package com.adrian.myplot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PlotCommand implements TabExecutor {
    private final MyPlot plugin;
    private final PlotManager pm;
    private final Set<UUID> deleteConfirm = new HashSet<>();

    public PlotCommand(MyPlot plugin) {
        this.plugin = plugin;
        this.pm = plugin.manager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "<red>Solo los jugadores pueden usar este comando.");
            return true;
        }
        if (args.length == 0) {
            help(p);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "claim" -> claim(p);
            case "buy", "comprar" -> buy(p);
            case "sell", "vender" -> sell(p, args);
            case "unsell" -> unsell(p);
            case "merge", "fusionar" -> merge(p, args);
            case "unmerge", "separar" -> unmerge(p, args);
            case "home", "h" -> home(p, args);
            case "visit", "v" -> visit(p, args);
            case "delete", "borrar" -> delete(p, args);
            case "info", "i" -> info(p);
            case "list" -> list(p);
            case "add" -> trust(p, args, true);
            case "remove" -> trust(p, args, false);
            case "border", "borde" -> border(p, args);
            case "limit", "limite" -> limit(p);
            case "creator", "creador" -> CreatorAnimation.play(plugin, p);
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        Msg.raw(p, "<gradient:gold:yellow><bold>MyPlot " + CreatorAnimation.VERSION + "</bold></gradient>");
        Msg.raw(p, "<yellow>/plot claim <gray>- reclamar la parcela donde estas");
        Msg.raw(p, "<yellow>/plot buy <gray>- comprar una parcela en venta");
        Msg.raw(p, "<yellow>/plot sell <precio> <gray>| <yellow>unsell <gray>- vender tu parcela");
        Msg.raw(p, "<yellow>/plot merge [direccion] <gray>- fusionar con la vecina");
        Msg.raw(p, "<yellow>/plot unmerge [direccion] <gray>- separar parcelas");
        Msg.raw(p, "<yellow>/plot home [n] <gray>- ir a tu parcela");
        Msg.raw(p, "<yellow>/plot visit <jugador> [n] <gray>- visitar la parcela de otro");
        Msg.raw(p, "<yellow>/plot info | list <gray>- informacion y tus parcelas");
        Msg.raw(p, "<yellow>/plot add|remove <jugador> <gray>- dar o quitar acceso");
        Msg.raw(p, "<yellow>/plot border [bloque] <gray>- cambiar el borde");
        Msg.raw(p, "<yellow>/plot limit <gray>- ver los limites de la parcela");
        Msg.raw(p, "<yellow>/plot delete <gray>- borrar tu parcela");
        Msg.raw(p, "<yellow>/plot creator <gray>- creador del plugin");
    }

    // ---------- Ayudas ----------

    private PlotId idHere(Player p) {
        PlotWorld pw = pm.getPlotWorld(p.getWorld());
        if (pw == null) {
            Msg.send(p, "<red>Este mundo no es un mundo de parcelas.");
            return null;
        }
        Location l = p.getLocation();
        PlotId id = pw.plotAt(l.getBlockX(), l.getBlockZ());
        if (id == null || pw.isSpawnPlot(id.x(), id.z())) {
            Msg.send(p, "<red>Debes estar dentro de una parcela.");
            return null;
        }
        return id;
    }

    private Plot ownedHere(Player p) {
        PlotId id = idHere(p);
        if (id == null) return null;
        Plot plot = pm.getPlot(id);
        if (plot == null) {
            Msg.send(p, "<red>Esta parcela no tiene dueño.");
            return null;
        }
        if (!plot.getOwner().equals(p.getUniqueId()) && !p.hasPermission("myplot.admin")) {
            Msg.send(p, "<red>Esta parcela no es tuya.");
            return null;
        }
        return plot;
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private int parseIndex(String[] args, int pos) {
        if (args.length <= pos) return 1;
        try {
            return Integer.parseInt(args[pos]);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private BlockFace parseFace(Player p, String[] args, int pos) {
        if (args.length > pos) {
            switch (args[pos].toLowerCase()) {
                case "norte", "n", "north" -> {
                    return BlockFace.NORTH;
                }
                case "sur", "s", "south" -> {
                    return BlockFace.SOUTH;
                }
                case "este", "e", "east" -> {
                    return BlockFace.EAST;
                }
                case "oeste", "o", "w", "west" -> {
                    return BlockFace.WEST;
                }
                default -> {
                    return null;
                }
            }
        }
        float yaw = ((p.getLocation().getYaw() % 360) + 360) % 360;
        if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private String faceName(BlockFace f) {
        return switch (f) {
            case NORTH -> "norte";
            case SOUTH -> "sur";
            case EAST -> "este";
            default -> "oeste";
        };
    }

    private boolean overLimit(Player p) {
        int max = plugin.getConfig().getInt("max-parcelas", 5);
        if (max > 0 && !p.hasPermission("myplot.unlimited") && pm.countOf(p.getUniqueId()) >= max) {
            Msg.send(p, "<red>Ya llegaste al limite de <yellow>" + max + "<red> parcelas.");
            return true;
        }
        return false;
    }

    private String money(double v) {
        return String.format(Locale.US, "%,.0f", v);
    }

    // ---------- Comandos ----------

    private void claim(Player p) {
        PlotId id = idHere(p);
        if (id == null) return;
        if (pm.getPlot(id) != null) {
            Msg.send(p, "<red>Esta parcela ya tiene dueño.");
            return;
        }
        if (pm.isClearing(id)) {
            Msg.send(p, "<red>La parcela se esta limpiando, intenta en unos segundos.");
            return;
        }
        if (overLimit(p)) return;
        double price = plugin.getConfig().getDouble("precio", 8000);
        if (plugin.getConfig().getBoolean("cobrar", true) && price > 0 && !p.hasPermission("myplot.free")) {
            EconomyHook eco = plugin.economy();
            if (eco == null) {
                Msg.send(p, "<red>El servidor no tiene economia (Vault). Avisa a un admin.");
                return;
            }
            if (!eco.has(p, price)) {
                Msg.send(p, "<red>No tienes suficiente dinero. Cuesta <gold>$" + money(price));
                return;
            }
            if (!eco.withdraw(p, price)) {
                Msg.send(p, "<red>No se pudo cobrar. Intenta de nuevo.");
                return;
            }
            Msg.send(p, "<gray>Se cobraron <gold>$" + money(price));
        }
        pm.claim(new Plot(id, p.getUniqueId(), p.getName(), pm.claimedBorder()));
        Msg.send(p, "<green>¡Parcela reclamada! <gray>(" + id.x() + ", " + id.z() + ")");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private void buy(Player p) {
        PlotId id = idHere(p);
        if (id == null) return;
        Plot plot = pm.getPlot(id);
        if (plot == null) {
            claim(p);
            return;
        }
        double price = plot.getSalePrice();
        if (price <= 0) {
            Msg.send(p, "<red>Esta parcela no esta en venta.");
            return;
        }
        if (plot.getOwner().equals(p.getUniqueId())) {
            Msg.send(p, "<red>Es tu parcela. Usa <yellow>/plot unsell<red> para quitarla de venta.");
            return;
        }
        if (overLimit(p)) return;
        EconomyHook eco = plugin.economy();
        if (eco == null) {
            Msg.send(p, "<red>El servidor no tiene economia (Vault). Avisa a un admin.");
            return;
        }
        if (!eco.has(p, price)) {
            Msg.send(p, "<red>No tienes suficiente dinero. Cuesta <gold>$" + money(price));
            return;
        }
        if (!eco.withdraw(p, price)) {
            Msg.send(p, "<red>No se pudo cobrar. Intenta de nuevo.");
            return;
        }
        double tax = Math.max(0, Math.min(100, plugin.getConfig().getDouble("impuesto-venta", 0)));
        double net = price * (1 - tax / 100.0);
        UUID sellerId = plot.getOwner();
        eco.deposit(Bukkit.getOfflinePlayer(sellerId), net);
        pm.transfer(plot, p.getUniqueId(), p.getName());
        Msg.send(p, "<green>¡Compraste la parcela por <gold>$" + money(price) + "<green>!");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        Player seller = Bukkit.getPlayer(sellerId);
        if (seller != null) {
            Msg.send(seller, "<yellow>" + p.getName() + "<green> compro tu parcela. Recibiste <gold>$" + money(net));
        }
    }

    private void sell(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<red>Uso: /plot sell <precio>");
            return;
        }
        Plot plot = ownedHere(p);
        if (plot == null) return;
        if (!plot.getMerged().isEmpty()) {
            Msg.send(p, "<red>No puedes vender una parcela fusionada. Separala antes con <yellow>/plot unmerge<red>.");
            return;
        }
        double price;
        try {
            price = Double.parseDouble(args[1]);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Precio invalido.");
            return;
        }
        pm.setSale(plot, price);
        Msg.send(p, "<green>Tu parcela esta en venta por <gold>$" + money(price)
                + "<green>. Quitala con <yellow>/plot unsell<green>.");
    }

    private void unsell(Player p) {
        Plot plot = ownedHere(p);
        if (plot == null) return;
        if (plot.getSalePrice() <= 0) {
            Msg.send(p, "<red>Esta parcela no esta en venta.");
            return;
        }
        pm.setSale(plot, 0);
        Msg.send(p, "<green>Ya no esta en venta.");
    }

    private void merge(Player p, String[] args) {
        Plot plot = ownedHere(p);
        if (plot == null) return;
        BlockFace face = parseFace(p, args, 1);
        if (face == null) {
            Msg.send(p, "<red>Direccion: norte, sur, este u oeste.");
            return;
        }
        String err = pm.merge(plot, face);
        if (err != null) {
            Msg.send(p, "<red>" + err);
            return;
        }
        Msg.send(p, "<green>¡Parcelas fusionadas hacia el <yellow>" + faceName(face) + "<green>!");
    }

    private void unmerge(Player p, String[] args) {
        Plot plot = ownedHere(p);
        if (plot == null) return;
        BlockFace face = parseFace(p, args, 1);
        if (face == null) {
            Msg.send(p, "<red>Direccion: norte, sur, este u oeste.");
            return;
        }
        String err = pm.unmerge(plot, face);
        if (err != null) {
            Msg.send(p, "<red>" + err);
            return;
        }
        Msg.send(p, "<green>Parcelas separadas hacia el <yellow>" + faceName(face) + "<green>.");
    }

    private void home(Player p, String[] args) {
        List<Plot> mine = pm.plotsOf(p.getUniqueId());
        if (mine.isEmpty()) {
            Msg.send(p, "<red>No tienes parcelas. Usa <yellow>/plot claim<red> en una parcela libre.");
            return;
        }
        int n = parseIndex(args, 1);
        if (n < 1 || n > mine.size()) {
            Msg.send(p, "<red>Tienes <yellow>" + mine.size() + "<red> parcela(s). Usa /plot home <numero>.");
            return;
        }
        Location target = pm.home(mine.get(n - 1));
        if (target == null) {
            Msg.send(p, "<red>El mundo de esa parcela no esta cargado.");
            return;
        }
        p.teleport(target);
        Msg.send(p, "<green>Teletransportado a tu parcela <yellow>#" + n);
    }

    private void visit(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<red>Uso: /plot visit <jugador> [numero]");
            return;
        }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            Msg.send(p, "<red>No encontre a ese jugador.");
            return;
        }
        List<Plot> theirs = pm.plotsOf(target.getUniqueId());
        int n = parseIndex(args, 2);
        if (theirs.isEmpty() || n < 1 || n > theirs.size()) {
            Msg.send(p, "<red>Ese jugador no tiene esa parcela.");
            return;
        }
        Location loc = pm.home(theirs.get(n - 1));
        if (loc == null) {
            Msg.send(p, "<red>El mundo de esa parcela no esta cargado.");
            return;
        }
        p.teleport(loc);
        Msg.send(p, "<green>Visitando la parcela de <yellow>" + args[1]);
    }

    private void delete(Player p, String[] args) {
        Plot plot = ownedHere(p);
        if (plot == null) return;
        boolean confirm = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        if (!confirm || !deleteConfirm.remove(p.getUniqueId())) {
            deleteConfirm.add(p.getUniqueId());
            Msg.send(p, "<red>¡Se borrara TODO lo construido y no hay reembolso! "
                    + "Escribe <yellow>/plot delete confirm<red> para confirmar.");
            return;
        }
        Msg.send(p, "<yellow>Borrando parcela...");
        pm.removeAndClear(plot.getId(), () -> Msg.send(p, "<green>Parcela borrada y lista para otro dueño."));
    }

    private void info(Player p) {
        PlotId id = idHere(p);
        if (id == null) return;
        Plot plot = pm.getPlot(id);
        Msg.raw(p, "<gold><bold>Parcela (" + id.x() + ", " + id.z() + ")");
        if (plot == null) {
            double price = plugin.getConfig().getDouble("precio", 8000);
            Msg.raw(p, "<gray>Estado: <green>libre <gray>- precio <gold>$" + money(price));
            return;
        }
        List<String> names = new ArrayList<>();
        for (UUID u : plot.getTrusted()) {
            String n = Bukkit.getOfflinePlayer(u).getName();
            names.add(n != null ? n : u.toString().substring(0, 8));
        }
        Msg.raw(p, "<gray>Dueño: <yellow>" + plot.getOwnerName());
        Msg.raw(p, "<gray>Con acceso: <yellow>" + (names.isEmpty() ? "nadie" : String.join(", ", names)));
        Msg.raw(p, "<gray>Borde: <yellow>" + plot.getBorder().name().toLowerCase());
        int group = pm.connected(id).size();
        if (group > 1) Msg.raw(p, "<gray>Fusionada con: <yellow>" + (group - 1) + " parcela(s)");
        if (plot.getSalePrice() > 0) {
            Msg.raw(p, "<gray>En venta por: <gold>$" + money(plot.getSalePrice()));
        }
    }

    private void list(Player p) {
        List<Plot> mine = pm.plotsOf(p.getUniqueId());
        if (mine.isEmpty()) {
            Msg.send(p, "<gray>No tienes parcelas.");
            return;
        }
        Msg.raw(p, "<gold><bold>Tus parcelas:");
        int i = 1;
        for (Plot pl : mine) {
            PlotId id = pl.getId();
            String sale = pl.getSalePrice() > 0 ? " <red>(en venta)" : "";
            Msg.raw(p, "<yellow>#" + i++ + " <gray>" + id.world() + " (" + id.x() + ", " + id.z() + ")" + sale);
        }
    }

    private void trust(Player p, String[] args, boolean add) {
        if (args.length < 2) {
            Msg.send(p, "<red>Uso: /plot " + (add ? "add" : "remove") + " <jugador>");
            return;
        }
        Plot plot = ownedHere(p);
        if (plot == null) return;
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            Msg.send(p, "<red>No encontre a ese jugador.");
            return;
        }
        if (target.getUniqueId().equals(plot.getOwner())) {
            Msg.send(p, "<red>Ese es el dueño.");
            return;
        }
        for (PlotId gid : pm.connected(plot.getId())) {
            Plot gp = pm.getPlot(gid);
            if (gp == null) continue;
            if (add) gp.getTrusted().add(target.getUniqueId());
            else gp.getTrusted().remove(target.getUniqueId());
        }
        pm.save();
        Msg.send(p, add ? "<green>" + args[1] + " ahora puede construir en tu parcela."
                : "<green>" + args[1] + " ya no tiene acceso.");
    }

    private void border(Player p, String[] args) {
        Plot plot = ownedHere(p);
        if (plot == null) return;
        if (args.length < 2) {
            p.openInventory(new BorderMenu(plot.getId(), pm.allowedBorders()).getInventory());
            return;
        }
        String name = args[1].toUpperCase(Locale.ROOT);
        Material m = Material.matchMaterial(name);
        if (m == null || !m.name().endsWith("_SLAB")) m = Material.matchMaterial(name + "_SLAB");
        if (m == null || !pm.allowedBorders().contains(m)) {
            Msg.send(p, "<red>Ese borde no esta disponible. Usa <yellow>/plot border<red> para ver el menu.");
            return;
        }
        pm.setBorder(plot, m);
        Msg.send(p, "<green>Borde cambiado a <yellow>" + m.name().toLowerCase());
    }

    private void limit(Player p) {
        PlotId id = idHere(p);
        if (id == null) return;
        PlotWorld pw = pm.getPlotWorld(p.getWorld());
        final double x0 = pw.minX(id.x());
        final double z0 = pw.minZ(id.z());
        final double x1 = pw.maxX(id.x()) + 1.0;
        final double z1 = pw.maxZ(id.z()) + 1.0;
        final double y = p.getLocation().getY() + 1.0;
        Msg.send(p, "<aqua>Mostrando los limites de la parcela unos segundos...");
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t++ >= 16) {
                    cancel();
                    return;
                }
                for (double i = x0; i <= x1; i += 1.0) {
                    p.spawnParticle(Particle.END_ROD, i, y, z0, 1, 0, 0, 0, 0);
                    p.spawnParticle(Particle.END_ROD, i, y, z1, 1, 0, 0, 0, 0);
                }
                for (double i = z0; i <= z1; i += 1.0) {
                    p.spawnParticle(Particle.END_ROD, x0, y, i, 1, 0, 0, 0, 0);
                    p.spawnParticle(Particle.END_ROD, x1, y, i, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ---------- Autocompletado ----------

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("claim", "buy", "sell", "unsell", "merge", "unmerge", "home", "visit",
                    "delete", "info", "list", "add", "remove", "border", "limit", "creator"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "add", "remove", "visit" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                }
                case "border", "borde" -> {
                    for (Material m : pm.allowedBorders()) {
                        out.add(m.name().replace("_SLAB", "").toLowerCase());
                    }
                }
                case "merge", "unmerge", "fusionar", "separar" -> out.addAll(List.of("norte", "sur", "este", "oeste"));
                case "delete" -> out.add("confirm");
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(prefix));
        return out;
    }
}
