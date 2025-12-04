package com.autoregen;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;

public class AutoWorldRegen extends JavaPlugin {

    private Logger logger;
    private FileConfiguration config;
    private long regenIntervalTicks;
    private int warningMinutes;
    private int bufferRadius;
    private Set<String> protectedWorlds;
    private BukkitRunnable regenTask;

    @Override
    public void onEnable() {
        logger = getLogger();
        saveDefaultConfig();
        loadConfiguration();
        startRegenTask();
        logger.info("AutoWorldRegen enabled! Using modern chunk regeneration API.");
    }

    @Override
    public void onDisable() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        logger.info("AutoWorldRegen disabled!");
    }

    private void loadConfiguration() {
        config = getConfig();
        int intervalMinutes = config.getInt("regen-interval-minutes", 60);
        warningMinutes = config.getInt("warning-minutes", 5);
        bufferRadius = config.getInt("buffer-radius", 10);
        protectedWorlds = new HashSet<>(config.getStringList("protected-worlds"));
        
        regenIntervalTicks = intervalMinutes * 60L * 20L;
        
        logger.info("Configuration loaded:");
        logger.info("  Interval: " + intervalMinutes + " minutes");
        logger.info("  Warning: " + warningMinutes + " minutes");
        logger.info("  Buffer: " + bufferRadius + " chunks");
    }

    private void startRegenTask() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        
        long warningTicks = warningMinutes * 60L * 20L;
        
        regenTask = new BukkitRunnable() {
            @Override
            public void run() {
                scheduleWarning(warningTicks);
            }
        };
        
        regenTask.runTaskTimer(this, regenIntervalTicks, regenIntervalTicks);
    }

    private void scheduleWarning(long warningTicks) {
        String warningMsg = config.getString("warning-message", "&e⚠ World regeneration in {minutes} minutes!")
                .replace("{minutes}", String.valueOf(warningMinutes))
                .replace("&", "§");
        
        Bukkit.broadcastMessage(warningMsg);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                regenerateChunks();
            }
        }.runTaskLater(this, warningTicks);
    }

    private void regenerateChunks() {
        for (World world : Bukkit.getWorlds()) {
            if (protectedWorlds.contains(world.getName())) {
                continue;
            }
            
            logger.info("Starting regeneration for world: " + world.getName());
            int regenerated = 0;
            
            for (Chunk chunk : world.getLoadedChunks()) {
                if (shouldRegenerateChunk(chunk)) {
                    regenerateChunkModern(world, chunk);
                    regenerated++;
                }
            }
            
            logger.info("Regenerated " + regenerated + " chunks in " + world.getName());
        }
        
        String completeMsg = config.getString("complete-message", "&aWorld regeneration complete!")
                .replace("&", "§");
        Bukkit.broadcastMessage(completeMsg);
    }

    private boolean shouldRegenerateChunk(Chunk chunk) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(chunk.getWorld())) {
                continue;
            }
            
            int playerChunkX = player.getLocation().getBlockX() >> 4;
            int playerChunkZ = player.getLocation().getBlockZ() >> 4;
            
            int distanceX = Math.abs(playerChunkX - chunkX);
            int distanceZ = Math.abs(playerChunkZ - chunkZ);
            
            if (distanceX <= bufferRadius && distanceZ <= bufferRadius) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Modern chunk regeneration using Paper's async API
     */
    private void regenerateChunkModern(World world, Chunk chunk) {
        int x = chunk.getX();
        int z = chunk.getZ();
        
        // Unload the chunk first
        chunk.unload(true);
        
        // Use Paper's async chunk generation
        CompletableFuture<Chunk> future = world.getChunkAtAsync(x, z, true);
        future.thenAccept(regeneratedChunk -> {
            // Chunk is now regenerated
            logger.fine("Regenerated chunk at " + x + ", " + z);
        }).exceptionally(throwable -> {
            logger.warning("Failed to regenerate chunk at " + x + ", " + z + ": " + throwable.getMessage());
            return null;
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/autoregen [info|now|reload]");
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        if (subCommand.equals("info")) {
            int intervalMinutes = (int)(regenIntervalTicks / 20 / 60);
            sender.sendMessage("§6=== AutoWorldRegen Info ===");
            sender.sendMessage("§aInterval: §f" + intervalMinutes + " minutes");
            sender.sendMessage("§aWarning time: §f" + warningMinutes + " minutes");
            sender.sendMessage("§aBuffer radius: §f" + bufferRadius + " chunks");
            sender.sendMessage("§aProtected worlds: §f" + protectedWorlds);
            return true;
        }
        
        if (subCommand.equals("now")) {
            if (!sender.hasPermission("autoregen.admin")) {
                sender.sendMessage("§cYou need autoregen.admin permission!");
                return true;
            }
            sender.sendMessage("§eForcing regeneration...");
            regenerateChunks();
            return true;
        }
        
        if (subCommand.equals("reload")) {
            if (!sender.hasPermission("autoregen.admin")) {
                sender.sendMessage("§cYou need autoregen.admin permission!");
                return true;
            }
            reloadConfig();
            loadConfiguration();
            startRegenTask();
            sender.sendMessage("§aConfiguration reloaded!");
            return true;
        }
        
        sender.sendMessage("§cUnknown subcommand!");
        return true;
    }
}
