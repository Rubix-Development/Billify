package nl.rubixstudios.billify;

import co.aikar.commands.PaperCommandManager;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import net.milkbowl.vault.economy.Economy;
import nl.rubixstudios.billify.command.InvoiceCommand;
import nl.rubixstudios.billify.data.Config;
import nl.rubixstudios.billify.data.ConfigFile;
import nl.rubixstudios.billify.data.Language;
import nl.rubixstudios.billify.invoice.InvoiceController;
import nl.rubixstudios.billify.util.ColorUtil;
import nl.rubixstudios.billify.util.VaultDownloader;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

@Getter
public final class Billify extends JavaPlugin {

    @Getter private static Billify instance;
    @Setter private boolean fullyEnabled;

    private Metrics metrics;

    private int serverMajorVersion;

    @Setter private ConfigFile configFile;
    @Setter private ConfigFile langFile;

    private Economy econ;
    private Gson gson;

    private InvoiceController invoiceController;

    private PaperCommandManager commandManager;

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration file
        if (!loadConfig()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Print plugin information
        printPluginInfo();

        // Check version validity
        if (!checkVersion()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Check Vault availability (and auto-download it if needed)
        if (!checkVault() && Config.getBoolean("ECONOMY.USE_VAULT")) {
            log("&cVault is required (ECONOMY.USE_VAULT is true) but could not be installed. Disabling Billify.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        registerGson();
        setupEconomy();

        invoiceController = new InvoiceController();

        // Initialize ACF and register the command
        commandManager = new PaperCommandManager(this);
        commandManager.registerCommand(new InvoiceCommand());

        // bStats metrics (https://bstats.org)
        int pluginId = 33589;
        metrics = new Metrics(this, pluginId);

        fullyEnabled = true;

        log("&aEnabled "  + getDescription().getName() + " &a!");
        log("");
        log("&e===&6=============================================&e===");
    }

    @Override
    public void onDisable() {
        if (fullyEnabled) {
            log("- &cDisabling "  + getDescription().getName() + " &c!");
            log("");
            if (invoiceController != null) {
                invoiceController.disable();
            }
            Bukkit.getServicesManager().unregisterAll(this);
            log("");

            metrics.shutdown();
        }
    }

    private boolean loadConfig() {
        try {
            configFile = new ConfigFile("config.yml");

            String languageFile = configFile.getString("LANGUAGE_FILE");
            if (languageFile == null || languageFile.isEmpty()) languageFile = "language_EN.yml";
            try {
                langFile = new ConfigFile(languageFile);
            } catch (RuntimeException ex) {
                log("&cLanguage file '" + languageFile + "' could not be loaded, falling back to language_EN.yml");
                langFile = new ConfigFile("language_EN.yml");
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            return false;
        }
        new Config();
        new Language();
        return true;
    }

    private void printPluginInfo() {
        log("&e===&6=============================================&e===");
        log("- &fName&7: &e"  + getDescription().getName());
        log("- &fVersion&7: &e"  + getDescription().getVersion());
        log("- &fAuthor&7: &e"  + getDescription().getAuthors());
        log("- &fSupport&7: &e"  + getDescription().getWebsite());
        log("");
    }

    private boolean checkVersion() {
        log("&eChecking version compatibility:");

        // Get the server version string
        String serverVersion = Bukkit.getServer().getVersion();

        // Extract the major version number
        int majorVersion;
        try {
            String[] versionParts = serverVersion.split("\\.");
            majorVersion = Integer.parseInt(versionParts[1].replaceAll("[^0-9].*$", "")); // The major version number is the second part
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false; // Unable to determine version, handle accordingly
        }
        this.serverMajorVersion = majorVersion;

        // Check if the major version is between 8 and 20 (inclusive)
        if (majorVersion >= 8 && majorVersion <= 20) {
            log("&aServer version %version% is compatible!".replace("%version%", String.valueOf(majorVersion)));
            return true;
        } else {
            log("&cServer version %version% is not compatible!".replace("%version%", String.valueOf(majorVersion)));
            return false;
        }
    }

    private boolean checkVault() {
        log("&eChecking Vault availability:");
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            if (Config.getBoolean("VAULT.AUTO_DOWNLOAD")) {
                if (!VaultDownloader.downloadAndLoad(serverMajorVersion)) {
                    return false;
                }
            } else {
                log("   &c&lVault integration is not available because it's not installed!");
                return false;
            }
        }
        log("   &aVault is installed and available.");
        log("");
        return true;
    }

    public void registerGson() {
        gson = new Gson();
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }
        econ = rsp.getProvider();
    }

    public void log(String message) {
        Bukkit.getConsoleSender().sendMessage(ColorUtil.translate(message));
    }
}
