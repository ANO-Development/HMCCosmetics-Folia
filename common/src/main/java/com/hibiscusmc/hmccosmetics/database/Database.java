package com.hibiscusmc.hmccosmetics.database;

import com.hibiscusmc.hmccosmetics.config.section.DatabaseSettings;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.database.types.Data;
import com.hibiscusmc.hmccosmetics.database.types.MySQLData;
import com.hibiscusmc.hmccosmetics.database.types.NoneData;
import com.hibiscusmc.hmccosmetics.database.types.SQLiteData;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import lombok.Getter;
import me.lojosho.hibiscuscommons.util.FoliaScheduler;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;

public class Database {

    @Getter
    private static Data data;
    private static final MySQLData MYSQL_DATA = new MySQLData();
    private static final SQLiteData SQLITE_DATA = new SQLiteData();
    private static final NoneData NONE_DATA = new NoneData();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1024),
            runnable -> {
                Thread thread = new Thread(runnable, "HMCCosmetics-Database");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );
    private static final ConcurrentHashMap<UUID, UserData> LATEST_SNAPSHOTS = new ConcurrentHashMap<>();
    private static volatile boolean acceptingTasks = true;
    private static volatile boolean shuttingDown;
    private static volatile CompletableFuture<Void> initialization = CompletableFuture.completedFuture(null);

    public Database() {
        String databaseType = DatabaseSettings.getDatabaseType();
        data = SQLITE_DATA; // default to SQLite, then check if it's anything different
        switch (databaseType.toLowerCase()) {
            case "mysql":
                data = MYSQL_DATA;
                break;
            case "sqlite":
                // already the default
                break;
            case "none":
                data = NONE_DATA;
                MessagesUtil.sendDebugMessages("Database is set to none. Data will not be saved.", Level.WARNING);
                break;
            default:
                MessagesUtil.sendDebugMessages("Invalid database type. Defaulting to SQLite.", Level.WARNING);
        }
        MessagesUtil.sendDebugMessages("Database is " + data);

        setup();
    }

    public static void setup() {
        initialization = execute(data::setup).whenComplete((ignored, exception) -> {
            if (exception == null) return;
            HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
            plugin.getLogger().log(Level.SEVERE, "Unable to initialize cosmetic data storage.", exception);
            FoliaScheduler.runGlobal(plugin, () -> plugin.getServer().getPluginManager().disablePlugin(plugin));
        });
    }

    public static void save(CosmeticUser user) {
        if (user == null) return;
        save(capture(user));
    }

    public static UserData capture(CosmeticUser user) {
        UserData snapshot = UserData.snapshot(user);
        LATEST_SNAPSHOTS.put(snapshot.getOwner(), snapshot);
        return snapshot;
    }

    public static void save(UserData snapshot) {
        if (snapshot == null || data == null) return;
        UserData immutableCopy = new UserData(snapshot);
        LATEST_SNAPSHOTS.put(immutableCopy.getOwner(), immutableCopy);
        data.save(immutableCopy);
    }

    public static void save(Player player) {
        save(CosmeticUsers.getUser(player));
    }

    public static CompletableFuture<UserData> get(UUID uniqueId) {
        return initialization.thenCompose(ignored -> data.get(uniqueId));
    }

    public static void clearData(UUID uniqueId) {
        data.clear(uniqueId);
    }

    public static CompletableFuture<Void> execute(Runnable task) {
        if (!acceptingTasks) return CompletableFuture.failedFuture(new RejectedExecutionException("Database executor is shutting down."));
        try {
            return CompletableFuture.runAsync(task, EXECUTOR);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static <T> CompletableFuture<T> supply(Supplier<T> task) {
        if (!acceptingTasks) return CompletableFuture.failedFuture(new RejectedExecutionException("Database executor is shutting down."));
        try {
            return CompletableFuture.supplyAsync(task, EXECUTOR);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public static synchronized void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        try {
            LATEST_SNAPSHOTS.values().forEach(snapshot -> data.save(new UserData(snapshot)));
            CompletableFuture<Void> closeFuture = execute(data::close);
            acceptingTasks = false;
            try {
                closeFuture.get(10L, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                HMCCosmeticsPlugin.getInstance().getLogger().warning("Timed out while flushing cosmetic data during shutdown.");
            } catch (ExecutionException exception) {
                HMCCosmeticsPlugin.getInstance().getLogger().log(Level.SEVERE, "Unable to close cosmetic data storage cleanly.", exception.getCause());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        } finally {
            EXECUTOR.shutdown();
            try {
                if (!EXECUTOR.awaitTermination(10L, TimeUnit.SECONDS)) EXECUTOR.shutdownNow();
            } catch (InterruptedException exception) {
                EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
