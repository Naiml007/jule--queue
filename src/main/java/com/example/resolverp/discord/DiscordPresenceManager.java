package com.example.resolverp.discord;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordPresenceManager {

    private static final Logger logger = LoggerFactory.getLogger(DiscordPresenceManager.class);
    private final DiscordRPC lib = DiscordRPC.INSTANCE;
    private String applicationId;
    private final AtomicBoolean rpcInitialized = new AtomicBoolean(false);
    private final AtomicBoolean callbacksRunning = new AtomicBoolean(false);
    private Thread callbackThread;

    private long startTimestamp = 0L;

    public boolean isInitialized() {
        return rpcInitialized.get();
    }

    public void init(String discordAppId) {
        if (rpcInitialized.get()) {
            logger.warn("Discord RPC already initialized.");
            return;
        }
        this.applicationId = discordAppId;
        logger.info("Initializing Discord RPC with Application ID: {}", applicationId);

        DiscordEventHandlers handlers = new DiscordEventHandlers();
        handlers.ready = (user) -> logger.info("Discord RPC Ready! User: {}#{}", user.username, user.discriminator);
        handlers.disconnected = (err, msg) -> logger.warn("Discord RPC Disconnected: error {} - {}", err, msg);
        handlers.errored = (err, msg) -> logger.error("Discord RPC Error: error {} - {}", err, msg);

        try {
            lib.Discord_Initialize(applicationId, handlers, true, null);
            rpcInitialized.set(true);

            if (callbackThread == null || !callbackThread.isAlive()) {
                callbacksRunning.set(true);
                callbackThread = new Thread(() -> {
                    while (callbacksRunning.get() && rpcInitialized.get()) {
                        try {
                            lib.Discord_RunCallbacks();
                            Thread.sleep(2000); // Recommended poll rate by library
                        } catch (InterruptedException e) {
                            logger.warn("Discord RPC callback thread interrupted.", e);
                            Thread.currentThread().interrupt();
                            callbacksRunning.set(false); // Exit loop if interrupted
                        } catch (UnsatisfiedLinkError ule) {
                            // This can happen if Discord is closed or pipe is broken
                            logger.error("UnsatisfiedLinkError in Discord_RunCallbacks. Discord might have been closed. Shutting down RPC.", ule);
                            shutdown(); // Attempt to clean up
                            callbacksRunning.set(false); // Exit loop
                        } catch (Exception e) {
                            logger.error("Exception in Discord RPC callback thread: {}", e.getMessage(), e);
                            // Potentially try to re-initialize or just log and continue trying
                            // For now, we'll let it continue trying to run callbacks, but if it's persistent,
                            // this could lead to log spam. A more robust solution might involve a backoff.
                        }
                    }
                    logger.info("Discord RPC callback thread finished.");
                }, "Discord-RPC-Callbacks");
                callbackThread.start();
            }
            logger.info("Discord RPC Initialized successfully.");
        } catch (UnsatisfiedLinkError e) {
            logger.error("Failed to initialize Discord RPC: Native library not found or Discord not running? {}", e.getMessage());
            // This usually means Discord is not running or the RPC library couldn't connect.
            rpcInitialized.set(false);
        } catch (Exception e) {
            logger.error("Failed to initialize Discord RPC: {}", e.getMessage(), e);
            rpcInitialized.set(false);
        }
    }

    public void updatePresence(String projectName, String stateDetails) {
        if (!rpcInitialized.get()) {
            logger.warn("Cannot update presence, Discord RPC not initialized.");
            return;
        }

        DiscordRichPresence presence = new DiscordRichPresence();
        presence.details = "Editing: " + projectName;
        presence.state = stateDetails; // e.g., "Color Grading", "Editing Timeline", or simply "Active"

        // You need to upload assets named "resolve_logo" and "davinci_resolve_icon"
        // to your Discord Application's Rich Presence art assets.
        presence.largeImageKey = "resolve_logo";
        presence.largeImageText = "DaVinci Resolve";
        // presence.smallImageKey = "davinci_resolve_icon"; // Optional
        // presence.smallImageText = "Working on " + projectName; // Optional

        if (startTimestamp == 0L) {
            startTimestamp = System.currentTimeMillis() / 1000; // Timestamp of when activity started
        }
        presence.startTimestamp = startTimestamp;

        lib.Discord_UpdatePresence(presence);
        logger.debug("Discord presence updated: Project - {}, Details - {}", projectName, stateDetails);
    }

    public void clearPresence() {
        if (!rpcInitialized.get()) {
            // Not much to do if not initialized
            return;
        }
        logger.info("Clearing Discord presence.");
        lib.Discord_ClearPresence();
        startTimestamp = 0L; // Reset timestamp for next session
    }


    public void shutdown() {
        if (!rpcInitialized.getAndSet(false)) { // Atomically set to false and get previous value
            logger.info("Discord RPC was not initialized or already shut down.");
            return;
        }

        logger.info("Shutting down Discord RPC...");
        callbacksRunning.set(false); // Signal callback thread to stop

        // Attempt to clear presence before shutting down
        try {
            DiscordRichPresence presence = new DiscordRichPresence(); // Create an empty presence
            lib.Discord_UpdatePresence(presence); // Clear presence
            lib.Discord_ClearPresence(); // Also try this
        } catch (UnsatisfiedLinkError e) {
            logger.warn("UnsatisfiedLinkError while trying to clear presence during shutdown. Discord might have already closed. {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Exception while trying to clear presence during shutdown: {}", e.getMessage(), e);
        }


        if (callbackThread != null && callbackThread.isAlive()) {
            try {
                callbackThread.interrupt(); // Interrupt it to stop Thread.sleep
                callbackThread.join(5000); // Wait for the callback thread to die
                if (callbackThread.isAlive()) {
                    logger.warn("Discord RPC callback thread did not terminate gracefully after 5 seconds.");
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for Discord RPC callback thread to shut down.", e);
                Thread.currentThread().interrupt();
            }
        }
        callbackThread = null;

        try {
            lib.Discord_Shutdown();
            logger.info("Discord RPC shut down successfully.");
        } catch (UnsatisfiedLinkError ule) {
             logger.warn("UnsatisfiedLinkError during Discord_Shutdown. Discord might have already closed. {}", ule.getMessage());
        } catch (Exception e) {
            logger.error("Error during Discord_Shutdown: {}", e.getMessage(), e);
        }
        startTimestamp = 0L; // Reset timestamp
    }
}
