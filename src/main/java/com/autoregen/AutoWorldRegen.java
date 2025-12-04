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
        
        // Sauvegarder la config par défaut
        saveDefaultConfig();
        loadConfiguration();
        
        // Démarrer la tâche de régénération
        startRegenTask();
        
        logger.info("AutoWorldRegen activé ! Intervalle: " + (regenIntervalTicks / 20 / 60) + " minutes");
    }

    @Override
    public void onDisable() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        logger.info("AutoWorldRegen désactivé !");
    }

    private void loadConfiguration() {
        config = getConfig();
        
        // Charger les paramètres
        int regenIntervalMinutes = config.getInt("regen-interval-minutes", 60);
        regenIntervalTicks = regenIntervalMinutes * 60L * 20L; // Convertir en ticks
        
        warningMinutes = config.getInt("warning-minutes", 5);
        bufferRadius = config.getInt("buffer-radius", 10);
        
        protectedWorlds = new HashSet<>(config.getStringList("protected-worlds"));
    }

    private void startRegenTask() {
        regenTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Avertissement X minutes avant
                scheduleWarning();
                
                // Régénération après le délai
                Bukkit.getScheduler().runTaskLater(AutoWorldRegen.this, () -> {
                    regenerateChunks();
                }, warningMinutes * 60L * 20L);
            }
        };
        
        regenTask.runTaskTimer(this, regenIntervalTicks, regenIntervalTicks);
    }

    private void scheduleWarning() {
        String message = config.getString("warning-message", 
            "&c[AutoRegen] Régénération des chunks dans " + warningMinutes + " minutes !");
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message.replace("&", "§"));
        }
        
        logger.info("Avertissement envoyé : régénération dans " + warningMinutes + " minutes");
    }

    private void regenerateChunks() {
        int chunksRegenerated = 0;
        
        for (World world : Bukkit.getWorlds()) {
            // Ignorer les mondes protégés
            if (protectedWorlds.contains(world.getName())) {
                continue;
            }
            
            Chunk[] loadedChunks = world.getLoadedChunks();
            
            for (Chunk chunk : loadedChunks) {
                // Ne pas régénérer si un joueur est proche
                if (isPlayerNearby(chunk, bufferRadius)) {
                    continue;
                }
                
                // Vérifier si le chunk est dans une claim GriefPrevention (optionnel)
                // if (isInClaim(chunk)) continue;
                
                // Régénérer le chunk
                world.regenerateChunk(chunk.getX(), chunk.getZ());
                chunksRegenerated++;
            }
        }
        
        logger.info("Régénération terminée : " + chunksRegenerated + " chunks régénérés");
        
        // Message aux joueurs
        String completeMessage = config.getString("complete-message", 
            "&a[AutoRegen] Régénération terminée !");
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(completeMessage.replace("&", "§"));
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
            
            // Calculer la distance en chunks
            int distanceX = Math.abs(chunkX - playerChunkX);
            int distanceZ = Math.abs(chunkZ - playerChunkZ);
            
            if (distanceX <= radius && distanceZ <= radius) {
                return true; // Un joueur est trop proche
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
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande !");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§e=== AutoWorldRegen ===");
            sender.sendMessage("§7/autoregen info §f- Afficher les informations");
            sender.sendMessage("§7/autoregen now §f- Forcer la régénération");
            sender.sendMessage("§7/autoregen reload §f- Recharger la config");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "info":
                sender.sendMessage("§e=== Informations ===");
                sender.sendMessage("§7Intervalle: §f" + (regenIntervalTicks / 20 / 60) + " minutes");
                sender.sendMessage("§7Avertissement: §f" + warningMinutes + " minutes avant");
                sender.sendMessage("§7Buffer: §f" + bufferRadius + " chunks");
                sender.sendMessage("§7Mondes protégés: §f" + protectedWorlds);
                break;
                
            case "now":
                if (!sender.hasPermission("autoregen.admin")) {
                    sender.sendMessage("§cPermission requise: autoregen.admin");
                    return true;
                }
                sender.sendMessage("§aRégénération forcée en cours...");
                regenerateChunks();
                break;
                
            case "reload":
                if (!sender.hasPermission("autoregen.admin")) {
                    sender.sendMessage("§cPermission requise: autoregen.admin");
                    return true;
                }
                reloadConfig();
                loadConfiguration();
                sender.sendMessage("§aConfiguration rechargée !");
                break;
                
            default:
                sender.sendMessage("§cCommande inconnue. Utilisez /autoregen pour l'aide");
                break;
        }
        
        return true;
    }
}
