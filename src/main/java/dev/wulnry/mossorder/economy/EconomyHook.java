package dev.wulnry.mossorder.economy;

import dev.wulnry.mossorder.MossOrder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault ekonomi servisi için ince bir köprü.
 * <p>
 * Tüm çağrılar ana thread üzerinde yapılmalıdır (Vault sağlayıcıları her zaman
 * thread güvenli değildir).
 */
public final class EconomyHook {

    private final MossOrder plugin;
    private Economy economy;

    public EconomyHook(MossOrder plugin) {
        this.plugin = plugin;
    }

    /**
     * Vault ekonomi sağlayıcısını bağlar.
     *
     * @return bağlanma başarılıysa true
     */
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        return economy != null;
    }

    public boolean isReady() {
        return economy != null;
    }

    public double balance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    /**
     * Oyuncudan para çeker.
     *
     * @return işlem başarılıysa true
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Oyuncuya para yatırır.
     *
     * @return işlem başarılıysa true
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
