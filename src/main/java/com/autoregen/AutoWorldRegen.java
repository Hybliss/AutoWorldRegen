package com.autoregen;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Claim;

import java.util.List;

public class AutoWorldRegen extends JavaPlugin {
    
    private GriefPrevention gp;
    private long taskId = -1;
    
    @Override
    public void onEnable() {
        // Sauvegarde config par défaut
        saveDefaultConfig();
        
        // Récupère GriefPrevention
        gp = GriefPrevention.instance;
        if (gp == null) {
            getLogger().severe("GriefPrevention non trouvé ! Plugin désactivé.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Démarre la régénération automatique
        startAutoRegen();
        
        getLogger().info("§a[AutoWorldRegen] Activé ! Intervalle: " + 
            getConfig().getInt("interval-days") + " jours");
    }
    
    @Override
    public void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask((int) taskId);
        }
        getLogger().info("§c[AutoWorldRegen] Désactivé !");
    }
    
    private void startAutoRegen() {
        int intervalDays = getConfig().getInt("interval-days", 7);
        long intervalTicks = 20L * 60 * 60 * 24 * intervalDays;
        
        taskId = new BukkitRunnable() {
            @Override
            public void run() {
                scheduleRegeneration();
            }
        }.runTaskTimer(this, intervalTicks, intervalTicks).getTaskId();
    }
    
    private void scheduleRegeneration() {
        List<Integer> warnings = getConfig().getIntegerList("warnings");
        
        // Avertissements progressifs
        for (int i = 0; i < warnings.size(); i++) {
            int minutes = warnings.get(i);
            long delay = 20L * 60 * (warnings.get(0) - minutes);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    String color = minutes <= 5 ? "§c" : "§6";
                    Bukkit.broadcastMessage(color + "[Île] Régénération dans " + 
                        minutes + " minute" + (minutes > 1 ? "s" : "") + " !");
                }
            }.runTaskLater(this, delay);
        }
        
        // Régénération finale
        long finalDelay = 20L * 60 * warnings.get(0);
        new BukkitRunnable() {
            @Override
            public void run() {
                executeRegeneration();
            }
        }.runTaskLater(this, finalDelay);
    }
    
    private void executeRegeneration() {
        Bukkit.broadcastMessage("§4[Île] RÉGÉNÉRATION EN COURS...");
        
        String worldName = getConfig().getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            getLogger().warning("Monde '" + worldName + "' non trouvé !");
            return;
        }
        
        int bufferChunks = getConfig().getInt("buffer-chunks", 2);
        int regenerated = 0;
        int protected = 0;
        
        // Téléporte les joueurs au spawn
        Location spawn = world.getSpawnLocation();
        for (Player player : world.getPlayers()) {
            player.teleport(spawn);
            player.sendMessage("§e[Île] Téléporté au spawn pour la régénération !");
        }
        
        // Régénère les chunks
        Chunk[] chunks = world.getLoadedChunks();
        for (Chunk chunk : chunks) {
            if (isProtected(chunk, bufferChunks)) {
                protected++;
                continue;
            }
            
            world.regenerateChunk(chunk.getX(), chunk.getZ());
            regenerated++;
        }
        
        String message = "§a[Île] Régénération terminée ! " +
            "§7(Régénérés: §e" + regenerated + "§7, Protégés: §e" + protected + "§7)";
        Bukkit.broadcastMessage(message);
        getLogger().info(message);
    }
    
    private boolean isProtected(Chunk chunk, int buffer) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        // Vérifie le chunk et ses voisins
        for (int x = -buffer; x <= buffer; x++) {
            for (int z = -buffer; z <= buffer; z++) {
                Location checkLoc = new Location(
                    world,
                    (chunkX + x) * 16 + 8,
                    64,
                    (chunkZ + z) * 16 + 8
                );
                
                Claim claim = gp.dataStore.getClaimAt(checkLoc, false, null);
                if (claim != null) {
                    return true; // Chunk protégé
                }
            }
        }
        
        return false;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("autoregen")) {
            
            if (!sender.hasPermission("autoregen.admin")) {
                sender.sendMessage("§cPermission refusée !");
                return true;
            }
            
            if (args.length == 0) {
                sender.sendMessage("§6=== AutoWorldRegen ===");
                sender.sendMessage("§e/autoregen reload §7- Recharge la config");
                sender.sendMessage("§e/autoregen now §7- Force la régénération");
                sender.sendMessage("§e/autoregen info §7- Affiche les infos");
                return true;
            }
            
            switch (args[0].toLowerCase()) {
                case "reload":
                    reloadConfig();
                    Bukkit.getScheduler().cancelTask((int) taskId);
                    startAutoRegen();
                    sender.sendMessage("§a[AutoWorldRegen] Configuration rechargée !");
                    break;
                    
                case "now":
                    sender.sendMessage("§e[AutoWorldRegen] Régénération forcée lancée !");
                    scheduleRegeneration();
                    break;
                    
                case "info":
                    sender.sendMessage("§6=== Informations ===");
                    sender.sendMessage("§7Intervalle: §e" + getConfig().getInt("interval-days") + " jours");
                    sender.sendMessage("§7Buffer: §e" + getConfig().getInt("buffer-chunks") + " chunks");
                    sender.sendMessage("§7Monde: §e" + getConfig().getString("world"));
                    break;
                    
                default:
                    sender.sendMessage("§cCommande inconnue ! Utilise /autoregen");
            }
            
            return true;
        }
        
        return false;
    }
}
