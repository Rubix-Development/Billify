package nl.rubixstudios.billify.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import nl.rubixstudios.billify.Billify;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadFactory;

/**
 * @author PingLagger on 28/07/2024
 * @project Billify
 */
public class TaskUtil {

    public static ThreadFactory newThreadFactory(String name) {
        return new ThreadFactoryBuilder().setNameFormat(name).build();
    }

    public static ThreadFactory newThreadFactory(String name, Thread.UncaughtExceptionHandler handler) {
        return new ThreadFactoryBuilder().setNameFormat(name).setUncaughtExceptionHandler(handler).build();
    }

    public static void sync(Callable callable) {
        Bukkit.getScheduler().runTask(Billify.getInstance(), callable::call);
    }

    public static BukkitTask syncLater(Callable callable, long delay) {
        return Bukkit.getScheduler().runTaskLater(Billify.getInstance(), callable::call, delay);
    }

    public static BukkitTask syncTimer(Callable callable, long delay, long value) {
        return Bukkit.getScheduler().runTaskTimer(Billify.getInstance(), callable::call, delay, value);
    }

    public static void async(Callable callable) {
        Bukkit.getScheduler().runTaskAsynchronously(Billify.getInstance(), callable::call);
    }

    public static BukkitTask asyncLater(Callable callable, long delay) {
        return Bukkit.getScheduler().runTaskLaterAsynchronously(Billify.getInstance(), callable::call, delay);
    }

    public static BukkitTask asyncTimer(Callable callable, long delay, long value) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(Billify.getInstance(), callable::call, delay, value);
    }

    public interface Callable {
        void call();
    }
}
