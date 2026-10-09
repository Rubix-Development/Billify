package nl.rubixstudios.billify.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    public static String translate(String line) {
        return ChatColor.translateAlternateColorCodes('&', hex(line));
    }

    public static String strip(String line) {
        return ChatColor.stripColor(line);
    }

    /** Turns "&#rrggbb" into the "&x&r&r&g&g&b&b" format Spigot understands (1.16+). */
    private static String hex(String line) {
        if (line == null || line.indexOf('#') < 0) return line;
        final Matcher matcher = HEX.matcher(line);
        final StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            final StringBuilder code = new StringBuilder("&x");
            for (char c : matcher.group(1).toCharArray()) code.append('&').append(c);
            matcher.appendReplacement(out, code.toString());
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
