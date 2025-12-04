package com.autoregen;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Logger;

public class AutoWorldRegen extends JavaPlugin {
    
    private Logger logger;
    private FileConfiguration config;
    private long regenIntervalTicks;
    private long warningTicks;
    private int bufferRadius;
    private List<String> protectedWorlds;
    
    @Override
    public void onEnable() {
        logger = getLogger();
        
        saveDefaultConfig();
        config = getConfig();
        
        loadConfiguration();
        
        logger.info("========================================");
        logger.info("AutoWorldRegen ENABLED");
        logger.info("Version: Paper 1.21.10 Compatible");
        logger.info("========================================");
        logger.info("Configuration:");
        logger.info("  Interval: " + (regenIntervalTicks / 1200) + " minutes");
        logger.info("  Warning: " + (warningTicks / 1200) + " minutes before regen");
        logger.info("  Buffer: " + bufferRadius + " chunks around players");
        logger.info("  Protected Worlds: " + protectedWorlds);
        logger.info("========================================");
        
        startRegenerationCycle();
    }
    
    private void loadConfiguration() {
        int intervalMinutes = config.getInt("regen-interval-minutes", 120);
        int warningMinutes = config.getInt("warning-minutes", 10);
        bufferRadius = config.getInt("buffer-radius", 10);
        protectedWorlds = config.getStringList("protected-worlds");
        
        regenIntervalTicks = intervalMinutes * 60L * 20L;
        warningTicks = warningMinutes * 60L * 20L;
    }
    
    private void startRegenerationCycle() {
        new BukkitRunnable() {
            @Override
            public void run() {
                sendWarning();
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        regenerateWorld();
                    }
                }.runTaskLater(AutoWorldRegen.this, warningTicks);
            }
        }.runTaskTimer(this, regenIntervalTicks, regenIntervalTicks);
    }
    
    private void sendWarning() {
        int minutes = (int) (warningTicks / 1200);
        String message = config.getString("warning-message", "&e⚠ World regeneration in {minutes} minutes!")
                .replace("{minutes}", String.valueOf(minutes));
        
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        logger.info("Warning sent: Regeneration in " + minutes + " minutes");
    }
    
    private void regenerateWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (protectedWorlds.contains(world.getName())) {
                logger.info("Skipping protected world: " + world.getName());
                continue;
            }
            
            regenerateWorldChunks(world);
        }
    }
    
    private void regenerateWorldChunks(World world) {
        logger.info("========================================");
        logger.info("Starting regeneration for world: " + world.getName());
        logger.info("========================================");
        
        Set<String> protectedChunks = getProtectedChunks(world);
        
        Chunk[] loadedChunks = world.getLoadedChunks();
        logger.info("Total loaded chunks: " + loadedChunks.length);
        logger.info("Protected chunks: " + protectedChunks.size());
        
        int regenerated = 0;
        int protectedCount = 0;
        
        for (Chunk chunk : loadedChunks) {
            String chunkKey = chunk.getX() + "," + chunk.getZ();
            
            if (protectedChunks.contains(chunkKey)) {
                protectedCount++;
                continue;
            }
            
            try {
                int x = chunk.getX();
                int z = chunk.getZ();
                
                world.regenerateChunk(x, z);
                
                regenerated++;
                
                if (regenerated % 10 == 0) {
                    logger.info("Progress: " + regenerated + " chunks regenerated...");
                }
                
            } catch (Exception e) {
                logger.warning("Failed to regenerate chunk " + chunkKey + ": " + e.getMessage());
            }
        }
        
        logger.info("========================================");
        logger.info("Regeneration Complete!");
        logger.info("  Regenerated: " + regenerated + " chunks");
        logger.info("  Protected: " + protectedCount + " chunks");
        logger.info("========================================");
        
        String completeMessage = config.getString("complete-message", "&a✅ World regeneration complete!");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', completeMessage));
    }
    
    private Set<String> getProtectedChunks(World world) {
        Set<String> protectedChunks = new HashSet<>();
        
        for (Player player : world.getPlayers()) {
            Chunk playerChunk = player.getLocation().getChunk();
            int centerX = playerChunk.getX();
            int centerZ = playerChunk.getZ();
            
            for (int dx = -bufferRadius; dx <= bufferRadius; dx++) {
                for (int dz = -bufferRadius; dz <= bufferRadius; dz++) {
                    String chunkKey = (centerX + dx) + "," + (centerZ + dz);
                    protectedChunks.add(chunkKey);
                }
            }
        }
        
        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") != null) {
            GriefPrevention gp = GriefPrevention.instance;
            
            for (Claim claim : gp.dataStore.getClaims()) {
                if (!claim.parent.getWorld().equals(world)) {
                    continue;
                }
                
                Location lesserCorner = claim.getLesserBoundaryCorner();
                Location greaterCorner = claim.getGreaterBoundaryCorner();
                
                int minChunkX = lesserCorner.getBlockX() >> 4;
                int minChunkZ = lesserCorner.getBlockZ() >> 4;
                int maxChunkX = greaterCorner.getBlockX() >> 4;
                int maxChunkZ = greaterCorner.getBlockZ() >> 4;
                
                for (int x = minChunkX; x <= maxChunkX; x++) {
                    for (int z = minChunkZ; z <= maxChunkZ; z++) {
                        protectedChunks.add(x + "," + z);
                    }
                }
            }
        }
        
        return protectedChunks;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("autoregen")) {
            return false;
        }
        
        if (!sender.hasPermission("autoregen.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission!");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "========== AutoWorldRegen ==========");
            sender.sendMessage(ChatColor.YELLOW + "/autoregen now" + ChatColor.WHITE + " - Force regeneration");
            sender.sendMessage(ChatColor.YELLOW + "/autoregen reload" + ChatColor.WHITE + " - Reload config");
            sender.sendMessage(ChatColor.YELLOW + "/autoregen status" + ChatColor.WHITE + " - Show status");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "now":
                sender.sendMessage(ChatColor.GREEN + "Starting forced regeneration...");
                regenerateWorld();
                break;
                
            case "status":
                sender.sendMessage(ChatColor.GOLD + "========== Status ==========");
                sender.sendMessage(ChatColor.YELLOW + "Interval: " + ChatColor.WHITE + (regenIntervalTicks / 1200) + " minutes");
                sender.sendMessage(ChatColor.YELLOW + "Warning: " + ChatColor.WHITE + (warningTicks / 1200) + " minutes");
                sender.sendMessage(ChatColor.YELLOW + "Buffer: " + ChatColor.WHITE + bufferRadius + " chunks");
                sender.sendMessage(ChatColor.YELLOW + "Protected Worlds: " + ChatColor.WHITE + protectedWorlds);
                
                for (World world : Bukkit.getWorlds()) {
                    if (!protectedWorlds.contains(world.getName())) {
                        sender.sendMessage(ChatColor.GREEN + "  ✓ " + world.getName() + ": " + world.getLoadedChunks().length + " chunks loaded");
                    }
                }
                break;
                
            case "reload":
                reloadConfig();
                config = getConfig();
                loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "✅ Configuration reloaded!");
                sender.sendMessage(ChatColor.YELLOW + "New interval: " + (regenIntervalTicks / 1200) + " minutes");
                break;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command! Use /autoregen for help");
        }
        
        return true;
    }
    
    @Override
    public void onDisable() {
        logger.info("========================================");
        logger.info("AutoWorldRegen DISABLED");
        logger.info("========================================");
    }
}
