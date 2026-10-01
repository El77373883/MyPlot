package com.adrian.myplot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX =
            "<gradient:gold:yellow><bold>MyPlot</bold></gradient> <dark_gray>» <gray>";

    private Msg() {
    }

    public static Component parse(String mini) {
        return MM.deserialize(mini);
    }

    public static void send(CommandSender to, String mini) {
        to.sendMessage(parse(PREFIX + mini));
    }

    public static void raw(CommandSender to, String mini) {
        to.sendMessage(parse(mini));
    }
}
