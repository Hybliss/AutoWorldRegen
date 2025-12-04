package com.autoregen;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Claim;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class AutoWorldRegen extends JavaPlugin {
    
    private Logger logger;
    private FileConfiguration config;
    private int regenIntervalTicks;
    private int warningTicks;
    private int bufferRadius;
    private List<String> protectedWorlds;
    private File snapshotFile;
    private Map<String, BlockSnapshot> worldSnapshot;
    
    // Classe pour stocker l'état d'un bloc
    private static class BlockSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        
        int x, y, z;
        Material type;
        byte data;
        
        public BlockSnapshot(int x, int y, int z, Material type, byte data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.data = data;
        }
        
        public String getKey() {
            return x + "," + y + "," + z;
        }
    }
    
    @Override
    public void onEnable() {
        logger = getLogger();
        
        // Crée la config par défaut
        saveDefaultConfig();
        config = getConfig();
        loadConfiguration();
        
        // Fichier de snapshot
        snapshotFile = new File(getDataFolder(), "world-snapshot.dat");
        worldSnapshot = new HashMap<>();
        
        logger.info("========================================");
        logger.info("AutoWorldRegen v2.0 ENABLED");
        logger.info("Mode: Block-by-Block Regeneration");
        logger.info("========================================");
        logger.info("Configuration:");
        logger.info("  Scan Interval: " + (regenIntervalTicks / 1200) + " minutes");
        logger.info("  Warning: " + (warningTicks / 1200) + " minutes before regen");
        logger.info("  Buffer: " + bufferRadius + " chunks around players");
        logger.info("  Protected Worlds: " + protectedWorlds);
        logger.info("========================================");
        
        // Charge ou crée le snapshot
        if (snapshotFile.exists()) {
            logger.info("Loading world snapshot...");
            loadSnapshot();
            logger.info("Snapshot loaded: " + worldSnapshot.size() + " blocks");
        } else {
            logger.info("No snapshot found. Creating initial snapshot...");
            createSnapshot();
        }
        
        // Lance le cycle de régénération
        startRegenerationCycle();
    }
    
    private void loadConfiguration() {
        regenIntervalTicks = config.getInt("regen-interval-minutes", 60) * 1200;
        warningTicks = config.getInt("warning-minutes", 5) * 1200;
        bufferRadius = config.getInt("buffer-radius", 5);
        protectedWorlds = config.getStringList("protected-worlds");
        
        if (protectedWorlds.isEmpty()) {
            protectedWorlds = Arrays.asList("world_nether", "world_the_end");
        }
    }
    
    // Crée un snapshot du monde actuel
    private void createSnapshot() {
        String worldName = config.getString("world-name", "world");
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            logger.warning("World not found: " + worldName);
            return;
        }
        
        worldSnapshot.clear();
        int count = 0;
        
        logger.info("Scanning world... This may take a while!");
        
        for (Chunk chunk : world.getLoadedChunks()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                        Block block = chunk.getBlock(x, y, z);
                        
                        // Ignore l'air et certains blocs
                        if (block.getType() == Material.AIR || 
                            block.getType() == Material.CAVE_AIR ||
                            block.getType() == Material.VOID_AIR) {
                            continue;
                        }
                        
                        int worldX = chunk.getX() * 16 + x;
                        int worldZ = chunk.getZ() * 16 + z;
                        
                        BlockSnapshot snapshot = new BlockSnapshot(
                            worldX, y, worldZ,
                            block.getType(),
                            block.getData()
                        );
                        
                        worldSnapshot.put(snapshot.getKey(), snapshot);
                        count++;
                        
                        // Log progress tous les 10000 blocs
                        if (count % 10000 == 0) {
                            logger.info("Scanned " + count + " blocks...");
                        }
                    }
                }
            }
        }
        
        logger.info("Snapshot created: " + count + " blocks saved");
        saveSnapshot();
    }
    
    // Sauvegarde le snapshot sur disque
    private void saveSnapshot() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new GZIPOutputStream(new FileOutputStream(snapshotFile)))) {
            oos.writeObject(worldSnapshot);
            logger.info("Snapshot saved to disk");
        } catch (IOException e) {
            logger.severe("Failed to save snapshot: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Charge le snapshot depuis le disque
    @SuppressWarnings("unchecked")
    private void loadSnapshot() {
        try (ObjectInputStream ois = new ObjectInputStream(
                new GZIPInputStream(new FileInputStream(snapshotFile)))) {
            worldSnapshot = (Map<String, BlockSnapshot>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.severe("Failed to load snapshot: " + e.getMessage());
            e.printStackTrace();
            worldSnapshot = new HashMap<>();
        }
    }
    
    private void startRegenerationCycle() {
        new BukkitRunnable() {
            private int ticksUntilRegen = regenIntervalTicks;
            
            @Override
            public void run() {
                ticksUntilRegen--;
                
                // Warning
                if (ticksUntilRegen == warningTicks) {
                    sendWarning();
                }
                
                // Régénération
                if (ticksUntilRegen <= 0) {
                    regenerateBlocks();
                    ticksUntilRegen = regenIntervalTicks;
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }
    
    private void sendWarning() {
        int minutes = warningTicks / 1200;
        String message = config.getString("warning-message", 
            "&e⚠️ ATTENTION : Régénération dans {minutes} minute(s) !")
            .replace("&", "§")
            .replace("{minutes}", String.valueOf(minutes));
        
        Bukkit.broadcastMessage(message);
        logger.info("Regeneration warning sent: " + minutes + " minutes");
    }
    
    private void regenerateBlocks() {
        String worldName = config.getString("world-name", "world");
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            logger.warning("World not found: " + worldName);
            return;
        }
        
        logger.info("========================================");
        logger.info("Starting block regeneration...");
        logger.info("========================================");
        
        Set<String> protectedAreas = getProtectedAreas(world);
        int restored = 0;
        int checked = 0;
        
        // Parcourt le snapshot
        for (BlockSnapshot snapshot : worldSnapshot.values()) {
            checked++;
            
            // Vérifie si la zone est protégée
            String areaKey = (snapshot.x >> 4) + "," + (snapshot.z >> 4);
            if (protectedAreas.contains(areaKey)) {
                continue;
            }
            
            // Compare avec le bloc actuel
            Block currentBlock = world.getBlockAt(snapshot.x, snapshot.y, snapshot.z);
            
            if (currentBlock.getType() != snapshot.type) {
                // RESTAURE le bloc
                currentBlock.setType(snapshot.type);
                if (snapshot.data != 0) {
                    currentBlock.setData(snapshot.data);
                }
                restored++;
            }
            
            // Log progress
            if (checked % 10000 == 0) {
                logger.info("Progress: " + checked + " blocks checked, " + restored + " restored");
            }
        }
        
        logger.info("========================================");
        logger.info("Regeneration Complete!");
        logger.info("  Checked: " + checked + " blocks");
        logger.info("  Restored: " + restored + " blocks");
        logger.info("========================================");
        
        // Message aux joueurs
        String message = config.getString("complete-message", "&a✅ Regeneration complete!")
                .replace("&", "§");
        Bukkit.broadcastMessage(message);
    }
    
    private Set<String> getProtectedAreas(World world) {
        Set<String> protectedAreas = new HashSet<>();
        
        // Protection autour des joueurs
        for (Player player : world.getPlayers()) {
            Chunk playerChunk = player.getLocation().getChunk();
            int centerX = playerChunk.getX();
            int centerZ = playerChunk.getZ();
            
            for (int dx = -bufferRadius; dx <= bufferRadius; dx++) {
                for (int dz = -bufferRadius; dz <= bufferRadius; dz++) {
                    protectedAreas.add((centerX + dx) + "," + (centerZ + dz));
                }
            }
        }
        
        // Protection GriefPrevention
        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") != null) {
            try {
                GriefPrevention gp = GriefPrevention.instance;
                
                for (Claim claim : gp.dataStore.getClaims()) {
                    Location lesserCorner = claim.getLesserBoundaryCorner();
                    if (!lesserCorner.getWorld().equals(world)) {
                        continue;
                    }
                    
                    Location greaterCorner = claim.getGreaterBoundaryCorner();
                    
                    int minChunkX = lesserCorner.getBlockX() >> 4;
                    int minChunkZ = lesserCorner.getBlockZ() >> 4;
                    int maxChunkX = greaterCorner.getBlockX() >> 4;
                    int maxChunkZ = greaterCorner.getBlockZ() >> 4;
                    
                    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                            protectedAreas.add(cx + "," + cz);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error reading GriefPrevention claims: " + e.getMessage());
            }
        }
        
        return protectedAreas;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("autoregen")) {
            return false;
        }
        
        if (!sender.hasPermission("autoregen.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "========== AutoWorldRegen v2.0 ==========");
            sender.sendMessage(ChatColor.YELLOW + "/autoregen now" + ChatColor.WHITE + " - Force regeneration");
            sender.sendMessage(ChatColor.YELLOW + "/autoregen snapshot" + ChatColor.WHITE + " - Create new snapshot");
            sender.sendMessage(ChatColor.YELLOW + "/autoregen reload" + ChatColor.WHITE + " - Reload config");
            sender.sendMessage(ChatColor.YELLOW + "/autoregen status" + ChatColor.WHITE + " - Show status");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "now":
                sender.sendMessage(ChatColor.GREEN + "Starting forced regeneration...");
                regenerateBlocks();
                break;
                
            case "snapshot":
                sender.sendMessage(ChatColor.YELLOW + "Creating new snapshot... This may take a while!");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        createSnapshot();
                        sender.sendMessage(ChatColor.GREEN + "✅ Snapshot created!");
                    }
                }.runTaskAsynchronously(this);
                break;
                
            case "status":
                sender.sendMessage(ChatColor.GOLD + "========== Status ==========");
                sender.sendMessage(ChatColor.YELLOW + "Scan Interval: " + ChatColor.WHITE + (regenIntervalTicks / 1200) + " minutes");
                sender.sendMessage(ChatColor.YELLOW + "Warning: " + ChatColor.WHITE + (warningTicks / 1200) + " minutes");
                sender.sendMessage(ChatColor.YELLOW + "Buffer: " + ChatColor.WHITE + bufferRadius + " chunks");
                sender.sendMessage(ChatColor.YELLOW + "Snapshot Size: " + ChatColor.WHITE + worldSnapshot.size() + " blocks");
                break;
                
            case "reload":
                reloadConfig();
                config = getConfig();
                loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "✅ Configuration reloaded!");
                break;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command! Use /autoregen for help");
        }
        
        return true;
    }
    
    @Override
    public void onDisable() {
        logger.info("========================================");
        logger.info("AutoWorldRegen v2.0 DISABLED");
        logger.info("========================================");
    }
}
