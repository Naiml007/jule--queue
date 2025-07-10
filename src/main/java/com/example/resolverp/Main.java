package com.example.resolverp;

import com.example.resolverp.discord.DiscordPresenceManager;
import com.example.resolverp.process.ProcessDetector;
import com.example.resolverp.util.ProjectNameExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final String DISCORD_APP_ID_ENV_VAR = "DISCORD_APP_ID";
    private static final long POLLING_INTERVAL_SECONDS = 15;

    private final ProcessDetector processDetector;
    private final ProjectNameExtractor projectNameExtractor;
    private final DiscordPresenceManager discordManager;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean resolveCurrentlyRunning = new AtomicBoolean(false); // More descriptive name
    private String currentDiscordAppId;
    private String lastKnownProjectName = "";


    public Main() {
        this.processDetector = new ProcessDetector();
        this.projectNameExtractor = new ProjectNameExtractor();
        this.discordManager = new DiscordPresenceManager();
        // Use a daemon thread for the scheduler so it doesn't prevent JVM exit
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Resolve-Presence-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        currentDiscordAppId = System.getenv(DISCORD_APP_ID_ENV_VAR);
        if (currentDiscordAppId == null || currentDiscordAppId.trim().isEmpty()) {
            logger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            logger.warn("!!! Discord Application ID not found in environment variable {}     !!!", DISCORD_APP_ID_ENV_VAR);
            logger.warn("!!! Please set this to your Discord Application's Client ID.          !!!");
            logger.warn("!!! The application may not be able to connect to Discord properly.   !!!");
            logger.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

            // Prompt for App ID if not found
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter Discord Application ID (or press Enter to exit if you've set it elsewhere): ");
            String inputId = scanner.nextLine();
            if (inputId == null || inputId.trim().isEmpty()) {
                logger.info("No Discord App ID provided via prompt. Attempting to proceed without it or exiting if critical...");
                // Depending on how critical App ID is at startup, you might exit here.
                // For this app, DiscordRPC init will fail, which is handled.
            } else {
                currentDiscordAppId = inputId.trim();
                logger.info("Using Discord Application ID from user input: {}", currentDiscordAppId);
            }
            scanner.close(); // Close scanner when done
        }

        if (currentDiscordAppId == null || currentDiscordAppId.trim().isEmpty()){
            logger.error("Discord Application ID is not set. Presence will not function. Exiting.");
            System.exit(1);
            return;
        }


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered. Shutting down application...");
            if (!scheduler.isShutdown()) {
                scheduler.shutdownNow();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warn("Scheduler did not terminate in time.");
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            if (discordManager.isInitialized()) {
                discordManager.shutdown();
            }
            logger.info("Application shut down completed.");
        }, "ResolveRP-ShutdownHook"));

        scheduler.scheduleAtFixedRate(this::checkResolveAndUpdatePresence, 0, POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS);

        logger.info("DaVinci Resolve Discord Presence service started. Polling every {} seconds.", POLLING_INTERVAL_SECONDS);
        logger.info("Using Discord App ID: {}", currentDiscordAppId);

        // Since the scheduler uses a daemon thread, the main thread would exit
        // immediately unless we keep it alive.
        // A common way is to wait for an event or just loop.
        // For a desktop app that runs in the background, this is fine.
        // If it were a GUI app, the GUI thread would keep it alive.
        try {
            // Keep main thread alive until shutdown hook is called (e.g. by Ctrl+C)
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(Long.MAX_VALUE);
            }
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted. Initiating shutdown sequence.");
            Thread.currentThread().interrupt(); // Propagate interrupt
            // System.exit(0) will trigger the shutdown hook
        } finally {
             // Ensure resources are cleaned up if InterruptedException is caught
            if (!scheduler.isShutdown()) {
                scheduler.shutdownNow();
            }
            if (discordManager.isInitialized()) {
                discordManager.shutdown();
            }
        }
    }

    private void checkResolveAndUpdatePresence() {
        try {
            Optional<String> windowTitleOpt = processDetector.getResolveWindowTitle();

            if (windowTitleOpt.isPresent()) {
                String windowTitle = windowTitleOpt.get();
                boolean firstDetectionThisSession = !resolveCurrentlyRunning.getAndSet(true);

                if (firstDetectionThisSession) {
                    logger.info("DaVinci Resolve process detected. Window Title: {}", windowTitle);
                    if (!discordManager.isInitialized()) {
                        // Attempt to initialize Discord RPC only if Resolve is found for the first time in a session
                        discordManager.init(currentDiscordAppId);
                    }
                }

                if (discordManager.isInitialized()) {
                    Optional<String> projectNameOpt = projectNameExtractor.extractProjectName(windowTitle);
                    String projectName = projectNameOpt.orElse("Unknown Project");
                    String stateDetails = "Working in Resolve"; // Default state

                    if (projectName.equals("Project Manager")) {
                        stateDetails = "In Project Manager";
                    } else if (projectName.equals("Unknown Project") && !windowTitle.toLowerCase().contains("davinci resolve")) {
                        // This check helps to ensure that we are not misinterpreting a generic window title
                        // that might appear if Resolve.exe is running but it's not the main application window
                        // (e.g. a background process or a crash reporter with "Resolve" in its name).
                        logger.warn("Window title '{}' detected for Resolve.exe, but does not seem to be the main Resolve window. Skipping Discord update.", windowTitle);
                        return;
                    }

                    // Update presence if:
                    // 1. It's the first time we've detected Resolve in this session (ensures presence is set initially).
                    // 2. The project name has changed since the last update.
                    if (firstDetectionThisSession || !projectName.equals(lastKnownProjectName)) {
                        logger.info("Updating Discord presence. Project: '{}', Details: '{}'. First detection this session: {}", projectName, stateDetails, firstDetectionThisSession);
                        lastKnownProjectName = projectName;
                        discordManager.updatePresence(projectName, stateDetails);
                    } else {
                        logger.trace("No change in project name ('{}'). Skipping Discord update.", projectName);
                    }
                } else if (firstDetectionThisSession) {
                    // If it's the first detection but Discord manager failed to initialize (e.g. no App ID, Discord closed)
                    logger.warn("DaVinci Resolve detected, but Discord Rich Presence is not initialized. Presence will not be updated.");
                }

            } else { // Resolve is not running or no active window title
                if (resolveCurrentlyRunning.getAndSet(false)) { // If it was true (Resolve was running), set to false and execute block
                    logger.info("DaVinci Resolve process no longer detected or window closed.");
                    lastKnownProjectName = ""; // Reset last known project name
                    if (discordManager.isInitialized()) {
                        discordManager.clearPresence();
                        // We keep DiscordRPC initialized to allow quick restarts of Resolve.
                        // The shutdown hook will handle the final Discord_Shutdown().
                    }
                }
            }
        } catch (Exception e) {
            // Catching general exceptions here to prevent the scheduler from stopping due to an unexpected error.
            logger.error("Unhandled exception in scheduled task 'checkResolveAndUpdatePresence': {}", e.getMessage(), e);
            // Optionally, add more specific error handling or re-throw if certain exceptions are critical.
        }
    }

    public static void main(String[] args) {
        String logLevel = System.getProperty("app.log.level", System.getenv().getOrDefault("APP_LOG_LEVEL", "info"));
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel);
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true");
        System.setProperty("org.slf4j.simpleLogger.showShortLogName", "true");
        // For more detailed logs during development:
        // System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");


        logger.info("Application starting - DaVinci Resolve Discord Rich Presence");
        Main app = new Main();
        app.start(); // This will block until interrupted or error
        logger.info("Application has finished or start() returned unexpectedly.");
    }
}
