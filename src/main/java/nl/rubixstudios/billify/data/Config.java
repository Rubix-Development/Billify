package nl.rubixstudios.billify.data;

import nl.rubixstudios.billify.Billify;

import java.io.File;
import java.util.List;

public class Config {

    public static File INVOICE_DIR;

    public static String LICENSE_KEY;

    public Config() {
        final ConfigFile config = Billify.getInstance().getConfigFile();

        INVOICE_DIR = new File(Billify.getInstance().getDataFolder(), "invoices");
        if (!INVOICE_DIR.exists()) INVOICE_DIR.mkdirs();

        LICENSE_KEY = config.getString("LICENSE_KEY");
    }

    public static String getString(String path) {
        final ConfigFile configFile = Billify.getInstance().getConfigFile();
        return configFile.getString(path);
    }

    public static int getInteger(String path) {
        final ConfigFile configFile = Billify.getInstance().getConfigFile();
        return configFile.getInt(path);
    }

    public static List<String> getStringList(String path) {
        final ConfigFile configFile = Billify.getInstance().getConfigFile();
        return configFile.getStringList(path);
    }

    public static double getDouble(String path) {
        final ConfigFile configFile = Billify.getInstance().getConfigFile();
        return configFile.getDouble(path);
    }

    public static boolean getBoolean(String path) {
        final ConfigFile configFile = Billify.getInstance().getConfigFile();
        return configFile.getBoolean(path);
    }
}
