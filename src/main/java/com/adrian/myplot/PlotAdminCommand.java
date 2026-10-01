package com.adrian.myplot;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class PlotAdminCommand implements TabExecutor {
    private final MyPlot plugin;
    private final PlotManager pm;

    public PlotAdminCommand(MyPlot plugin) {
        this.plugin = plugin;
        this.pm = plugin.manager();
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("myplot.admin")) {
            Msg.send(s, "<red>No tienes permiso.");
            return true;
        }
        if (a.length == 0) {
            usage(s);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "create" -> create(s, a);
            case "delete" -> delete(s, a);
            case "limit" -> limit(s, a);
            case "list" -> list(s);
            case "tp" -> tp(s, a);
            case "reload" -> {
                plugin.reloadConfig();
                pm.loadConfigValues();
                Msg.send(s, "<green>Configuracion recargada.");
            }
            case "setprice" -> setPrice(s, a);
            case "reset" -> reset(s, a);
            default -> usage(s);
        }
        return true;
    }

    private void usage(CommandSender s) {
        Msg.raw(s, "<gold><bold>MyPlot Admin");
        Msg.raw(s, "<yellow>/plotadmin create plotworld <nombre> <tamaño> [limite]");
        Msg.raw(s, "<yellow>/plotadmin delete plotworld <nombre>");
        Msg.raw(s, "<yellow>/plotadmin limit <mundo> <bloques|off> <gray>- radio maximo del mundo");
        Msg.raw(s, "<yellow>/plotadmin list <gray>| <yellow>tp <nombre> <gray>| <yellow>reload");
        Msg.raw(s, "<yellow>/plotadmin setprice <precio>");
        Msg.raw(s, "<yellow>/plotadmin reset <jugador> <gray>- borra todas sus parcelas");
    }

    private void create(CommandSender s, String[] a) {
        if (a.length < 4 || !a[1].equalsIgnoreCase("plotworld")) {
            Msg.send(s, "<red>Uso: /plotadmin create plotworld <nombre> <tamaño> [limite]");
            return;
        }
        String name = a[2];
        if (!name.matches("[A-Za-z0-9_-]+")) {
            Msg.send(s, "<red>El nombre solo puede tener letras, numeros, _ y -");
            return;
        }
        int size;
        try {
            size = Integer.parseInt(a[3]);
        } catch (NumberFormatException e) {
            Msg.send(s, "<red>El tamaño debe ser un numero (ejemplo: 35).");
            return;
        }
        if (size < 8 || size > 256) {
            Msg.send(s, "<red>El tamaño debe estar entre 8 y 256.");
            return;
        }
        int limit = 0;
        if (a.length > 4) {
            try {
                limit = Integer.parseInt(a[4]);
            } catch (NumberFormatException e) {
                Msg.send(s, "<red>El limite debe ser un numero de bloques (ejemplo: 5000).");
                return;
            }
            if (limit < 100) {
                Msg.send(s, "<red>El limite minimo es de 100 bloques.");
                return;
            }
        }
        if (Bukkit.getWorld(name) != null || new File(Bukkit.getWorldContainer(), name).exists()) {
            Msg.send(s, "<red>Ya existe un mundo o carpeta con ese nombre.");
            return;
        }
        Msg.send(s, "<yellow>Creando mundo, puede tardar unos segundos...");
        World w = pm.createPlotWorld(name, size, limit);
        if (w == null) {
            Msg.send(s, "<red>No se pudo crear el mundo.");
            return;
        }
        Msg.send(s, "<green>Mundo <yellow>" + name + "<green> creado: parcelas de <yellow>" + size + "x" + size
                + "<green> con calles de <yellow>" + PlotWorld.ROAD + "<green> bloques"
                + (limit > 0 ? " y limite de <yellow>" + limit + "<green> bloques desde el centro." : "."));
        if (s instanceof Player p) p.teleport(pm.getPlotWorld(name).spawnLocation(w));
    }

    private void limit(CommandSender s, String[] a) {
        if (a.length < 3) {
            Msg.send(s, "<red>Uso: /plotadmin limit <mundo> <bloques|off>");
            return;
        }
        if (pm.getPlotWorld(a[1]) == null) {
            Msg.send(s, "<red>Ese mundo no existe en MyPlot.");
            return;
        }
        int limit = 0;
        if (!a[2].equalsIgnoreCase("off")) {
            try {
                limit = Integer.parseInt(a[2]);
            } catch (NumberFormatException e) {
                Msg.send(s, "<red>Escribe un numero de bloques o <yellow>off<red>.");
                return;
            }
            if (limit < 100) {
                Msg.send(s, "<red>El limite minimo es de 100 bloques.");
                return;
            }
        }
        pm.setWorldLimit(a[1], limit);
        Msg.send(s, limit > 0
                ? "<green>Limite de <yellow>" + a[1] + "<green>: <yellow>" + limit + "<green> bloques desde el centro."
                : "<green>Limite de <yellow>" + a[1] + "<green> desactivado.");
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 3 || !a[1].equalsIgnoreCase("plotworld")) {
            Msg.send(s, "<red>Uso: /plotadmin delete plotworld <nombre>");
            return;
        }
        if (pm.deletePlotWorld(a[2])) {
            Msg.send(s, "<green>Mundo quitado de MyPlot. La carpeta del mundo no se borra "
                    + "(borrala a mano si quieres).");
        } else {
            Msg.send(s, "<red>Ese mundo no existe en MyPlot.");
        }
    }

    private void list(CommandSender s) {
        if (pm.allWorlds().isEmpty()) {
            Msg.send(s, "<gray>No hay mundos de parcelas.");
            return;
        }
        Msg.raw(s, "<gold><bold>Mundos de parcelas:");
        for (PlotWorld pw : pm.allWorlds()) {
            String lim = pw.limit() > 0 ? " - limite " + pw.limit() : " - sin limite";
            Msg.raw(s, "<yellow>" + pw.name() + " <gray>- parcelas de " + pw.size() + "x" + pw.size() + lim);
        }
    }

    private void tp(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "<red>Solo jugadores.");
            return;
        }
        if (a.length < 2) {
            Msg.send(s, "<red>Uso: /plotadmin tp <nombre>");
            return;
        }
        PlotWorld pw = pm.getPlotWorld(a[1]);
        World w = Bukkit.getWorld(a[1]);
        if (pw == null || w == null) {
            Msg.send(s, "<red>Ese mundo no existe.");
            return;
        }
        p.teleport(pw.spawnLocation(w));
    }

    private void setPrice(CommandSender s, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "<red>Uso: /plotadmin setprice <precio>");
            return;
        }
        try {
            double v = Double.parseDouble(a[1]);
            if (v < 0) throw new NumberFormatException();
            plugin.getConfig().set("precio", v);
            plugin.saveConfig();
            Msg.send(s, "<green>Nuevo precio: <gold>$" + a[1]);
        } catch (NumberFormatException e) {
            Msg.send(s, "<red>Precio invalido.");
        }
    }

    private void reset(CommandSender s, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "<red>Uso: /plotadmin reset <jugador>");
            return;
        }
        Player online = Bukkit.getPlayerExact(a[1]);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(a[1]);
        if (target == null) {
            Msg.send(s, "<red>No encontre a ese jugador.");
            return;
        }
        List<Plot> theirs = new ArrayList<>(pm.plotsOf(target.getUniqueId()));
        if (theirs.isEmpty()) {
            Msg.send(s, "<gray>Ese jugador no tiene parcelas.");
            return;
        }
        for (Plot p : theirs) pm.removeAndClear(p.getId(), null);
        Msg.send(s, "<green>Se estan borrando <yellow>" + theirs.size() + "<green> parcela(s) de " + a[1]);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("myplot.admin")) return out;
        if (a.length == 1) {
            out.addAll(List.of("create", "delete", "limit", "list", "tp", "reload", "setprice", "reset"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "create", "delete" -> out.add("plotworld");
                case "tp", "limit" -> {
                    for (PlotWorld pw : pm.allWorlds()) out.add(pw.name());
                }
                case "reset" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                }
                default -> {
                }
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("delete")) {
            for (PlotWorld pw : pm.allWorlds()) out.add(pw.name());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("limit")) {
            out.add("off");
        }
        String prefix = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(prefix));
        return out;
    }
}
