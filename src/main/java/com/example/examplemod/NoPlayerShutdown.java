package com.example.examplemod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * No Player Shutdown - A NeoForge mod that automatically shuts down the server
 * after a configurable period of time when no players are online.
 */
@Mod(NoPlayerShutdown.MODID)
public class NoPlayerShutdown {
    public static final String MODID = "noplayershutdown";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Server instance reference
    private MinecraftServer server;
    
    // Scheduler for the shutdown timer
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> shutdownTask;
    
    // Track if shutdown is scheduled
    private boolean shutdownScheduled = false;
    
    // Track the time when server became empty (in milliseconds)
    private long emptyStartTime = -1;

    public NoPlayerShutdown(IEventBus modEventBus, ModContainer modContainer) {
        // Register ourselves for server and other game events
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        
        LOGGER.info("No Player Shutdown initialized!");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        shutdownScheduled = false;
        emptyStartTime = -1;
        
        LOGGER.info("No Player Shutdown is now monitoring player count.");
        LOGGER.info("Server will shut down after {} minutes of being empty.", Config.SHUTDOWN_DELAY_MINUTES.get());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Clean up the scheduler when server stops
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        shutdownScheduled = false;
        emptyStartTime = -1;
        LOGGER.info("No Player Shutdown shutting down.");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (server == null || !server.isDedicatedServer()) return;
        
        // Cancel any scheduled shutdown when a player joins
        cancelShutdown();
        
        int playerCount = server.getPlayerCount() + 1; // +1 because the player hasn't been fully added yet
        LOGGER.info("Player {} joined. Players online: {}", event.getEntity().getName().getString(), playerCount);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (server == null || !server.isDedicatedServer()) return;
        
        int playerCount = server.getPlayerCount() - 1; // -1 because the player hasn't been fully removed yet
        LOGGER.info("Player {} left. Players online: {}", event.getEntity().getName().getString(), playerCount);
        
        // Check if server is now empty
        if (playerCount <= 0) {
            scheduleShutdown();
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (server == null || !server.isDedicatedServer()) return;
        
        // Only check periodically (every 20 ticks = 1 second) to avoid performance impact
        if (server.getTickCount() % 20 != 0) return;
        
        // Log countdown warnings
        if (shutdownScheduled && emptyStartTime > 0) {
            long elapsedMinutes = (System.currentTimeMillis() - emptyStartTime) / 60000;
            long shutdownDelay = Config.SHUTDOWN_DELAY_MINUTES.get();
            long remainingMinutes = shutdownDelay - elapsedMinutes;
            
            // Log at specific intervals
            if (remainingMinutes > 0 && (remainingMinutes == 15 || remainingMinutes == 10 || 
                remainingMinutes == 5 || remainingMinutes == 1)) {
                // Only log once per minute
                long elapsedSeconds = (System.currentTimeMillis() - emptyStartTime) / 1000;
                if (elapsedSeconds % 60 < 1) {
                    LOGGER.warn("Server will shut down in {} minute(s) due to no players online.", remainingMinutes);
                }
            }
        }
    }

    private void scheduleShutdown() {
        if (shutdownScheduled) return;
        
        int delayMinutes = Config.SHUTDOWN_DELAY_MINUTES.get();
        emptyStartTime = System.currentTimeMillis();
        
        LOGGER.warn("Server is empty! Scheduling shutdown in {} minutes...", delayMinutes);
        
        shutdownTask = scheduler.schedule(() -> {
            if (server != null && server.isRunning()) {
                // Double-check that server is still empty
                if (server.getPlayerCount() == 0) {
                    LOGGER.warn("Shutting down server due to no players being online for {} minutes.", delayMinutes);
                    // Schedule the halt on the main server thread
                    server.execute(() -> server.halt(false));
                } else {
                    LOGGER.info("Shutdown cancelled - players are now online.");
                    shutdownScheduled = false;
                    emptyStartTime = -1;
                }
            }
        }, delayMinutes, TimeUnit.MINUTES);
        
        shutdownScheduled = true;
    }

    private void cancelShutdown() {
        if (shutdownScheduled && shutdownTask != null) {
            shutdownTask.cancel(false);
            shutdownScheduled = false;
            emptyStartTime = -1;
            LOGGER.info("Shutdown cancelled - a player has joined the server.");
        }
    }
}
