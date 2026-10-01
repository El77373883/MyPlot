package com.adrian.myplot;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CreatorAnimation {
    public static final String CREATOR = "soyadrianyt001";
    public static final String VERSION = "v1.0";
    private static final int DURATION = 100;
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    private CreatorAnimation() {
    }

    public static void play(MyPlot plugin, Player player) {
        long now = System.currentTimeMillis();
        Long last = COOLDOWNS.get(player.getUniqueId());
        if (last != null && now - last < 8000) {
            Msg.send(player, "<red>Espera unos segundos para repetirlo.");
            return;
        }
        COOLDOWNS.put(player.getUniqueId(), now);

        BossBar bar = BossBar.bossBar(
                Msg.parse("<gradient:gold:yellow><bold>✦ MyPlot " + VERSION + " ✦</bold></gradient>"),
                0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    player.hideBossBar(bar);
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                bar.progress(Math.min(1f, tick / (float) DURATION));

                // Titulo letra por letra
                String word = "MyPlot";
                if (tick <= 18 && tick % 3 == 0) {
                    int n = Math.min(word.length(), tick / 3 + 1);
                    showTitle(player, word.substring(0, n), tick, false);
                    player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.8f + n * 0.15f);
                } else if (tick > 18 && tick < 90 && tick % 4 == 0) {
                    showTitle(player, word, tick, true);
                }

                // Espiral de particulas
                double angle = tick * 0.5;
                double height = (tick % 44) * 0.05;
                for (int k = 0; k < 2; k++) {
                    double a = angle + k * Math.PI;
                    Location p = loc.clone().add(Math.cos(a) * 1.5, height, Math.sin(a) * 1.5);
                    player.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.FLAME, p, 1, 0, 0, 0, 0);
                }

                if (tick == 0) player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                if (tick == 30 || tick == 60 || tick == 90) {
                    firework(loc);
                    player.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                }

                if (tick >= DURATION) {
                    firework(loc);
                    player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    player.hideBossBar(bar);
                    Msg.raw(player, "<gradient:gold:yellow><bold>MyPlot</bold></gradient> <green>" + VERSION);
                    Msg.raw(player, "<gray>Plugin creado por <aqua>" + CREATOR);
                    cancel();
                    return;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void showTitle(Player p, String text, int tick, boolean withSub) {
        String ph = String.format(Locale.US, "%.2f", Math.sin(tick / 8.0));
        Component title = Msg.parse("<bold><gradient:gold:aqua:light_purple:" + ph + ">" + text + "</gradient></bold>");
        Component sub = withSub
                ? Msg.parse("<gray>Plugin por <aqua>" + CREATOR + " <dark_gray>• <green>" + VERSION)
                : Component.empty();
        p.showTitle(Title.title(title, sub,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(500))));
    }

    private static void firework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc.clone().add(0, 1, 0), Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.ORANGE, Color.AQUA)
                .withFade(Color.PURPLE)
                .flicker(true)
                .trail(true)
                .build());
        fw.setFireworkMeta(meta);
        fw.detonate();
    }
}
