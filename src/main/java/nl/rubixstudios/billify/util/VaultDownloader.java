package nl.rubixstudios.billify.util;

import nl.rubixstudios.billify.Billify;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads and loads the correct Vault release for the running server version
 * from the official MilkBowl GitHub releases when Vault is not installed.
 */
public class VaultDownloader {

    private static final String DOWNLOAD_URL = "https://github.com/MilkBowl/Vault/releases/download/%s/Vault.jar";

    /**
     * Picks the Vault release that matches the server's major version.
     * 1.13+ -> Vault 1.7.3, older (1.8 - 1.12) -> Vault 1.7.2.
     */
    public static String getVaultVersionFor(int serverMajorVersion) {
        return serverMajorVersion >= 13 ? "1.7.3" : "1.7.2";
    }

    public static boolean downloadAndLoad(int serverMajorVersion) {
        final Billify plugin = Billify.getInstance();
        final String vaultVersion = getVaultVersionFor(serverMajorVersion);
        final File pluginsFolder = plugin.getDataFolder().getParentFile();
        final File vaultFile = new File(pluginsFolder, "Vault.jar");

        plugin.log("   &eVault is not installed. Downloading Vault " + vaultVersion
                + " for server version 1." + serverMajorVersion + "...");

        if (!vaultFile.exists() && !download(String.format(DOWNLOAD_URL, vaultVersion), vaultFile)) {
            plugin.log("   &cFailed to download Vault. Install it manually from https://github.com/MilkBowl/Vault/releases");
            return false;
        }

        try {
            final Plugin vault = Bukkit.getPluginManager().loadPlugin(vaultFile);
            if (vault == null) {
                plugin.log("   &cDownloaded Vault.jar could not be loaded.");
                return false;
            }
            vault.onLoad();
            Bukkit.getPluginManager().enablePlugin(vault);
            plugin.log("   &aVault " + vaultVersion + " downloaded and enabled successfully.");
            return true;
        } catch (Exception ex) {
            plugin.log("   &cFailed to load the downloaded Vault.jar: " + ex.getMessage());
            plugin.log("   &cIt will be picked up automatically after a server restart.");
            return false;
        }
    }

    private static boolean download(String url, File target) {
        final File tempFile = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Billify-Plugin");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }

            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(tempFile)) {
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            if (target.exists() && !target.delete()) {
                tempFile.delete();
                return false;
            }
            return tempFile.renameTo(target);
        } catch (Exception ex) {
            tempFile.delete();
            return false;
        }
    }
}
