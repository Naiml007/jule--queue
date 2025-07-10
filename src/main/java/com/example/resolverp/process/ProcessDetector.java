package com.example.resolverp.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Optional;

public class ProcessDetector {

    private static final Logger logger = LoggerFactory.getLogger(ProcessDetector.class);
    private static final String RESOLVE_EXECUTABLE_NAME = "Resolve.exe";
    private static final String TASKLIST_COMMAND = "tasklist /v /fo csv /fi \"imagename eq " + RESOLVE_EXECUTABLE_NAME + "\"";

    public Optional<String> getResolveWindowTitle() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("win")) {
            logger.warn("Process detection via tasklist is only supported on Windows. OS: {}", osName);
            return Optional.empty();
        }

        try {
            Process process = Runtime.getRuntime().exec(TASKLIST_COMMAND);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                // Skip header line
                reader.readLine();

                while ((line = reader.readLine()) != null) {
                    logger.debug("Parsing tasklist line: {}", line);
                    String[] values = line.split("\",\""); // Split by ","

                    // Trim quotes from the first and last values if they exist
                    for (int i = 0; i < values.length; i++) {
                        values[i] = values[i].replaceAll("^\"|\"$", "");
                    }

                    if (values.length > 0 && RESOLVE_EXECUTABLE_NAME.equalsIgnoreCase(values[0])) {
                        // Window title is typically the last field in /v /fo csv output.
                        // Example line: "Resolve.exe","PID","SessionName","Session#","MemUsage","Status","User","CPUTime","WindowTitle"
                        // The 9th column (index 8) is WindowTitle.
                        if (values.length >= 9) {
                            String windowTitle = values[8].trim();
                            logger.debug("Found Resolve process with window title: {}", windowTitle);
                            if (!"N/A".equalsIgnoreCase(windowTitle) && !windowTitle.isEmpty()) {
                                return Optional.of(windowTitle);
                            } else {
                                logger.debug("Resolve process found, but window title is 'N/A' or empty. It might be starting up or running in the background.");
                                return Optional.empty(); // Or a specific status indicating "running but no active window"
                            }
                        } else {
                            logger.warn("Resolve process found, but tasklist output format is unexpected. Line: {}", line);
                        }
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("tasklist command exited with code: {}", exitCode);
                 try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    errorReader.lines().forEach(errorLine -> logger.error("tasklist error: {}", errorLine));
                }
            }

        } catch (IOException e) {
            logger.error("IOException while trying to detect Resolve process: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.error("InterruptedException while waiting for tasklist command: {}", e.getMessage(), e);
            Thread.currentThread().interrupt(); // Restore interrupted status
        }

        logger.debug("DaVinci Resolve process not found or no active window title.");
        return Optional.empty();
    }

    // Basic test
    public static void main(String[] args) {
        // Configure SLF4J simple logger for testing
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        ProcessDetector detector = new ProcessDetector();
        Optional<String> title = detector.getResolveWindowTitle();
        if (title.isPresent()) {
            System.out.println("DaVinci Resolve Window Title: " + title.get());
        } else {
            System.out.println("DaVinci Resolve process not found or no active window.");
        }
    }
}
