package com.example.examplemod;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * No Player Shutdown - A Forge mod that automatically shuts down the server
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
    
    // Track if we've checked the initial empty state after server start
    private boolean initialEmptyCheckDone = false;
    
    // Track when the server started (in milliseconds)
    private long serverStartTime = -1;

    public NoPlayerShutdown() {
        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        LOGGER.info("No Player Shutdown initialized!");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        shutdownScheduled = false;
        emptyStartTime = -1;
        initialEmptyCheckDone = false;
        serverStartTime = System.currentTimeMillis();
        
        LOGGER.info("No Players Shutdown is now monitoring player count.");
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
        initialEmptyCheckDone = false;
        serverStartTime = -1;
        LOGGER.info("No Players Shutdown shutting down.");
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (server == null || !server.isDedicatedServer()) return;
        
        // Only check periodically (every 20 ticks = 1 second) to avoid performance impact
        if (server.getTickCount() % 20 != 0) return;
        
        // Check if server started empty (wait 5 seconds after server start to ensure it's fully initialized)
        if (!initialEmptyCheckDone && serverStartTime > 0) {
            long timeSinceStart = System.currentTimeMillis() - serverStartTime;
            if (timeSinceStart >= 5000) { // Wait 5 seconds after server start
                initialEmptyCheckDone = true;
                // Check if server is empty and schedule shutdown if needed
                if (server.getPlayerCount() == 0 && !shutdownScheduled) {
                    scheduleShutdown();
                }
            }
        }
        
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
