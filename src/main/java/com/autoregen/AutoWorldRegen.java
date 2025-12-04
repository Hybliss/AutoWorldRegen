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
        logger.info("AutoWorldRegen enabled!");
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
        int regenIntervalMinutes = config.getInt("regen-interval-minutes", 60);
        regenIntervalTicks = regenIntervalMinutes * 60L * 20L;
        warningMinutes = config.getInt("warning-minutes", 5);
        bufferRadius = config.getInt("buffer-radius", 10);
        protectedWorlds = new HashSet<>(config.getStringList("protected-worlds"));
    }

    private void startRegenTask() {
        regenTask = new BukkitRunnable() {
            @Override
            public void run() {
                scheduleWarning();
                Bukkit.getScheduler().runTaskLater(AutoWorldRegen.this, new Runnable() {
                    @Override
                    public void run() {
                        regenerateChunks();
                    }
                }, warningMinutes * 60L * 20L);
            }
        };
        regenTask.runTaskTimer(this, regenIntervalTicks, regenIntervalTicks);
    }

    private void scheduleWarning() {
        String message = config.getString("warning-message", "Regeneration in " + warningMinutes + " minutes!");
        message = message.replace("&", "§").replace("{minutes}", String.valueOf(warningMinutes));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        logger.info("Warning sent to players");
    }

    private void regenerateChunks() {
        int chunksRegenerated = 0;
        for (World world : Bukkit.getWorlds()) {
            if (protectedWorlds.contains(world.getName())) {
                continue;
            }
            Chunk[] loadedChunks = world.getLoadedChunks();
            for (Chunk chunk : loadedChunks) {
                if (isPlayerNearby(chunk, bufferRadius)) {
                    continue;
                }
                world.regenerateChunk(chunk.getX(), chunk.getZ());
                chunksRegenerated++;
            }
        }
        logger.info("Regenerated " + chunksRegenerated + " chunks");
        String completeMessage = config.getString("complete-message", "Regeneration complete!");
        completeMessage = completeMessage.replace("&", "§");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(completeMessage);
        }
    }

    private boolean isPlayerNearby(Chunk chunk, int radius) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        World world = chunk.getWorld();
        for (Player player : world.getPlayers()) {
            Chunk playerChunk = player.getLocation().getChunk();
            int playerChunkX = playerChunk.getX();
            int playerChunkZ = playerChunk.getZ();
            int distanceX = Math.abs(chunkX - playerChunkX);
            int distanceZ = Math.abs(chunkZ - playerChunkZ);
            if (distanceX <= radius && distanceZ <= radius) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("autoregen")) {
            return false;
        }
        
        if (!sender.hasPermission("autoregen.use")) {
            sender.sendMessage("§cYou don't have permission!");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /autoregen <info|now|reload>");
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        if (subCommand.equals("info")) {
            long intervalMinutes = regenIntervalTicks / 20 / 60;
            sender.sendMessage("§aRegen interval: §f" + intervalMinutes + " minutes");
            sender.sendMessage("§aWarning time: §f" + warningMinutes + " minutes");
            sender.sendMessage("§aBuffer radius: §f" + bufferRadius + " chunks");
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
            sender.sendMessage("§aConfiguration reloaded!");
            return true;
        }
        
        sender.sendMessage("§cUnknown subcommand!");
        return true;
    }
}
