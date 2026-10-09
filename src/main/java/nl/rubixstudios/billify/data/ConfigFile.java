package nl.rubixstudios.billify.data;

import lombok.Getter;
import nl.rubixstudios.billify.Billify;
import nl.rubixstudios.billify.util.ColorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ConfigFile extends YamlConfiguration {

    private static final Billify mainInstance = Billify.getInstance();

    @Getter
    private final File file;

    public ConfigFile(String name) {
        this.file = new File(mainInstance.getDataFolder(), name);

        if (!this.file.exists()) {
            mainInstance.saveResource(name, false);
        }

        try {
            this.load(this.file);
        } catch (IOException | InvalidConfigurationException e) {
            logError(name, e);
            throw new RuntimeException("Failed to load configuration file: " + name, e);
        }

        // Keys added in newer versions fall back to the bundled file, so old configs keep working.
        // Custom language files fall back to the English one.
        InputStream defaults = mainInstance.getResource(name);
        if (defaults == null && name.startsWith("language")) defaults = mainInstance.getResource("language_EN.yml");
        if (defaults != null) {
            try (Reader reader = new InputStreamReader(defaults, StandardCharsets.UTF_8)) {
                this.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (IOException ignored) {
            }
        }
    }

    private void logError(String name, Exception e) {
        mainInstance.log("&cError occurred while loading " + name + ":");
        Stream.of(e.getMessage().split("\n")).forEach(line -> mainInstance.log("&c" + line));
        mainInstance.log("&e===&6=============================================&e===");
    }

    public void save() {
        try {
            this.save(this.file);
        } catch (IOException e) {
            mainInstance.log("&cFailed to save configuration file: " + file.getName());
        }
    }

    public ConfigurationSection getSection(String name) {
        return super.getConfigurationSection(name);
    }

    @Override
    public int getInt(String path) {
        return super.getInt(path);
    }

    @Override
    public double getDouble(String path) {
        return super.getDouble(path);
    }

    @Override
    public boolean getBoolean(String path) {
        return super.getBoolean(path);
    }

    @Override
    public String getString(String path) {
        final String value = super.getString(path);
        return ColorUtil.translate(value != null ? value : "");
    }

    @Override
    public List<String> getStringList(String path) {
        return super.getStringList(path).stream().map(ColorUtil::translate).collect(Collectors.toList());
    }

    public String center(String value, int maxLength) {
        StringBuilder builder = new StringBuilder(maxLength - value.length());
        IntStream.range(0, maxLength - value.length()).forEach(i -> builder.append(" "));
        builder.insert((builder.length() / 2) + 1, value);
        return builder.toString();
    }
}
