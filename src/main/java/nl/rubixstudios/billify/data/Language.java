package nl.rubixstudios.billify.data;

import nl.rubixstudios.billify.Billify;

import java.util.List;

public class Language {

    public static String getMessage(String path) {
        final ConfigFile langFile = Billify.getInstance().getLangFile();
        return langFile.getString(path);
    }

    public static List<String> getMessageList(String path) {
        final ConfigFile langFile = Billify.getInstance().getLangFile();
        return langFile.getStringList(path);
    }
}
