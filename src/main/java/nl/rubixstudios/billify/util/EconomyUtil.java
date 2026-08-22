package nl.rubixstudios.billify.util;

import net.milkbowl.vault.economy.Economy;
import nl.rubixstudios.billify.Billify;
import nl.rubixstudios.billify.data.Config;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Central economy access. Uses Vault (Essentials, CMI, etc.) when available and
 * ECONOMY.USE_VAULT is true, otherwise falls back to the configured console commands.
 */
public class EconomyUtil {

    private static Economy econ() {
        return Billify.getInstance().getEcon();
    }

    public static boolean useVault() {
        return Config.getBoolean("ECONOMY.USE_VAULT") && econ() != null;
    }

    public static boolean hasBalanceSupport() {
        return useVault();
    }

    public static double getBalance(OfflinePlayer player) {
        if (useVault()) {
            return econ().getBalance(player);
        }
        return 0.0D;
    }

    public static boolean has(OfflinePlayer player, double amount) {
        if (useVault()) {
            return econ().has(player, amount);
        }
        // Command mode cannot check balances; assume the payment is possible.
        return true;
    }

    public static boolean withdraw(OfflinePlayer player, double amount) {
        if (useVault()) {
            return econ().withdrawPlayer(player, amount).transactionSuccess();
        }
        return dispatch(Config.getString("ECONOMY.REMOVE_BALANCE_COMMAND"), player, amount);
    }

    public static boolean deposit(OfflinePlayer player, double amount) {
        if (useVault()) {
            return econ().depositPlayer(player, amount).transactionSuccess();
        }
        return dispatch(Config.getString("ECONOMY.ADD_BALANCE_COMMAND"), player, amount);
    }

    public static String format(double amount) {
        if (useVault()) {
            return econ().format(amount);
        }
        return String.format("%.2f", amount);
    }

    private static boolean dispatch(String command, OfflinePlayer player, double amount) {
        if (command == null || command.isEmpty() || player.getName() == null) return false;
        final String finalCommand = command
                .replace("%player%", player.getName())
                .replace("%amount%", String.valueOf(amount));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        return true;
    }
}
