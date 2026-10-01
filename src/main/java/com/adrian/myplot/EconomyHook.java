package com.adrian.myplot;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyHook {
    private Economy eco;

    public boolean setup() {
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        eco = rsp.getProvider();
        return eco != null;
    }

    public boolean has(OfflinePlayer p, double amount) {
        return eco.has(p, amount);
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        EconomyResponse r = eco.withdrawPlayer(p, amount);
        return r.transactionSuccess();
    }

    public void deposit(OfflinePlayer p, double amount) {
        eco.depositPlayer(p, amount);
    }
}
