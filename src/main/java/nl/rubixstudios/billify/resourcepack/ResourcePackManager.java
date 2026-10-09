package nl.rubixstudios.billify.resourcepack;

import lombok.Getter;
import nl.rubixstudios.billify.Billify;
import nl.rubixstudios.billify.data.Config;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Sends the Billify resource pack and remembers which players have it loaded.
 * Players without the pack always get the classic item menus.
 */
public class ResourcePackManager implements Listener {

    @Getter private static ResourcePackManager instance;

    private final Set<UUID> loaded = new HashSet<>();
    private final boolean supported;

    public ResourcePackManager() {
        instance = this;
        this.supported = supportsCustomModelData();
        GuiFont.load();

        if (Config.getBoolean("RESOURCE_PACK.ENABLED") && !supported) {
            Billify.getInstance().log("&cThe Billify resource pack needs Minecraft 1.14 or newer, using the classic menus.");
        }
        Bukkit.getPluginManager().registerEvents(this, Billify.getInstance());
    }

    public static boolean hasPack(Player player) {
        if (instance == null || !instance.supported || !Config.getBoolean("RESOURCE_PACK.ENABLED")) return false;
        return Config.getBoolean("RESOURCE_PACK.ASSUME_LOADED") || instance.loaded.contains(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!supported || !Config.getBoolean("RESOURCE_PACK.ENABLED") || !Config.getBoolean("RESOURCE_PACK.SEND_ON_JOIN")) return;

        final String url = Config.getString("RESOURCE_PACK.URL");
        if (url == null || url.isEmpty()) return;

        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(Billify.getInstance(), () -> {
            if (!player.isOnline()) return;
            final byte[] hash = sha1();
            if (hash != null) {
                player.setResourcePack(url, hash);
            } else {
                player.setResourcePack(url);
            }
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        // Compared by name: the set of statuses differs between server versions.
        final String status = event.getStatus().name();
        if (status.equals("SUCCESSFULLY_LOADED")) {
            loaded.add(event.getPlayer().getUniqueId());
        } else if (status.equals("DECLINED") || status.startsWith("FAILED") || status.equals("INVALID_URL") || status.equals("DISCARDED")) {
            loaded.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    private byte[] sha1() {
        String sha1 = Config.getString("RESOURCE_PACK.SHA1");
        if (sha1 == null || sha1.isEmpty()) return null;
        if (sha1.equalsIgnoreCase("AUTO")) sha1 = GuiFont.getPackSha1();
        if (sha1.length() != 40) return null;

        final byte[] bytes = new byte[20];
        for (int i = 0; i < 20; i++) {
            bytes[i] = (byte) Integer.parseInt(sha1.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static boolean supportsCustomModelData() {
        try {
            ItemMeta.class.getMethod("setCustomModelData", Integer.class);
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }
}
