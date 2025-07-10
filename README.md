# DaVinci Resolve Discord Rich Presence

This Java application provides Discord Rich Presence integration for DaVinci Resolve. It detects when DaVinci Resolve is running, extracts the current project name from the window title, and displays this information on your Discord profile.

## Features

- Detects DaVinci Resolve process and window title (Windows only for now).
- Extracts the current project name.
- Updates Discord Rich Presence with project details and status.
- Periodically checks for Resolve's status and updates Discord accordingly.
- Graceful shutdown of Discord RPC.

## Prerequisites

- **Java Development Kit (JDK) 11 or newer:** Make sure you have JDK installed and configured.
- **Apache Maven:** Used for building the project.
- **Discord Desktop Application:** Discord must be running to display Rich Presence.
- **DaVinci Resolve:** The application this tool integrates with.

## Setup & Configuration

### 1. Create a Discord Application

1.  Go to the [Discord Developer Portal](https://discord.com/developers/applications).
2.  Click on "**New Application**" in the top right corner.
3.  Give your application a name (e.g., "DaVinci Resolve Status") and click "**Create**".
4.  Navigate to the "**OAuth2**" -> "**General**" page. Under "CLIENT INFORMATION", you will find your **CLIENT ID**. This is your **Discord Application ID**. Copy this ID.
5.  (Optional but Recommended) Go to the "**Rich Presence**" -> "**Art Assets**" page.
    *   Upload an image to be used as the large image for your presence. Name it something like `resolve_logo`.
    *   You can also upload a small image if desired.
    *   **Note:** It might take a few minutes for new assets to become available via the API.

### 2. Set Discord Application ID

You need to provide the **Client ID** (Application ID) obtained in the previous step to this application. This can be done in one of two ways:

*   **Environment Variable (Recommended):**
    Set an environment variable named `DISCORD_APP_ID` to your Client ID.
    *   On Windows (PowerShell):
        ```powershell
        $env:DISCORD_APP_ID="YOUR_CLIENT_ID_HERE"
        ```
        (To set it permanently, search for "Edit the system environment variables")
    *   On Linux/macOS:
        ```bash
        export DISCORD_APP_ID="YOUR_CLIENT_ID_HERE"
        ```
        (Add this to your `.bashrc`, `.zshrc`, or shell profile for persistence)

*   **Interactive Prompt:**
    If the environment variable is not set, the application will prompt you to enter the Application ID when it starts.

## Building the Application

1.  Clone this repository or download the source code.
2.  Open a terminal or command prompt in the root directory of the project (where `pom.xml` is located).
3.  Run the following Maven command to build the application:

    ```bash
    mvn clean package
    ```
    This will compile the code and create an executable JAR file in the `target/` directory (e.g., `resolve-discord-presence-1.0-SNAPSHOT.jar`).

## Running the Application

1.  Ensure Discord is running on your system.
2.  Ensure you have set the `DISCORD_APP_ID` environment variable or are ready to enter it when prompted.
3.  Open a terminal or command prompt.
4.  Navigate to the `target/` directory where the JAR file was created.
5.  Run the application using the following command:

    ```bash
    java -jar resolve-discord-presence-1.0-SNAPSHOT.jar
    ```
    (Replace `resolve-discord-presence-1.0-SNAPSHOT.jar` with the actual name of the generated JAR file if it differs.)

The application will start polling for DaVinci Resolve. When Resolve is opened and a project is loaded, your Discord status should update.

### Logging

The application uses SLF4J for logging. By default, the log level is set to `INFO`. You can change the log level by setting the `APP_LOG_LEVEL` environment variable or the `app.log.level` Java system property.
Available levels: `trace`, `debug`, `info`, `warn`, `error`.

For example, to run with debug logging:
```bash
java -Dapp.log.level=debug -jar resolve-discord-presence-1.0-SNAPSHOT.jar
```
or set environment variable `APP_LOG_LEVEL=debug` before running.

## How It Works

-   **Process Detection:** The application uses the `tasklist` command on Windows to find the `Resolve.exe` process and its main window title.
-   **Project Name Extraction:** It parses the window title (e.g., "My Project - DaVinci Resolve") using regular expressions to get the current project name.
-   **Discord Integration:** It uses the `java-discord-rpc` library to communicate with the Discord client and update the Rich Presence status.
-   **Polling:** A scheduled task runs every 15 seconds (default) to check for DaVinci Resolve's status and update Discord.

## Troubleshooting

-   **No Presence Update:**
    *   Ensure Discord is running.
    *   Verify your `DISCORD_APP_ID` is correct and accessible to the application.
    *   Check the application logs for any errors (e.g., connection issues, RPC errors).
    *   Make sure DaVinci Resolve has a project open and its window title is visible (not minimized to tray in a way that hides the title).
    *   If you just uploaded Rich Presence assets, wait 5-10 minutes for Discord to cache them.
-   **"Failed to initialize Discord RPC: Native library not found or Discord not running?" error:**
    *   This usually means the Discord client is not running or could not be connected to. Ensure Discord is open and logged in.
    *   On some systems, running the JAR as administrator might be required if Discord is running as administrator.
-   **Window Title Not Detected:**
    *   The `tasklist` command might behave differently on some Windows versions or configurations. The application looks for `Resolve.exe`.
    *   If Resolve is running but the title isn't detected, check the logs. The title might be "N/A" if no project is fully open or if Resolve is in a specific state (e.g., splash screen).

## Manual Test Steps

To manually test the application, follow these steps:

1.  **Preparation:**
    *   Ensure you have completed the "Setup & Configuration" steps above (JDK, Maven, Discord App ID set, Discord running).
    *   Build the application (`mvn clean package`) if you haven't already.
    *   Open the application's log output in a terminal so you can monitor its activity.

2.  **Test Case 1: Application Start - Resolve NOT Running**
    *   Ensure DaVinci Resolve is **not** running.
    *   Run the Java application: `java -jar target/resolve-discord-presence-1.0-SNAPSHOT.jar`.
    *   **Expected:**
        *   The application logs should indicate it's polling for Resolve.
        *   No Discord Rich Presence should appear for DaVinci Resolve.
        *   If `DISCORD_APP_ID` was not set as an environment variable, it should prompt you for it. If you provide it, it should then try to connect. If you don't, it should state it cannot proceed without an App ID.

3.  **Test Case 2: Start Resolve - Open Project**
    *   While the Java application is running (and successfully initialized with an App ID), start DaVinci Resolve.
    *   Open or create a project in Resolve (e.g., "Test Project 1").
    *   **Expected:**
        *   Within the polling interval (default 15 seconds), the application logs should show that Resolve was detected and the window title was read.
        *   Your Discord profile should update to show "Playing DaVinci Resolve Status" (or your app name).
        *   The details should show something like "Editing: Test Project 1".
        *   The state might show "Working in Resolve" or similar.
        *   A large image (if configured like `resolve_logo` in your Discord App and in `DiscordPresenceManager.java`) should appear.

4.  **Test Case 3: Resolve Running - Switch Projects**
    *   While Resolve and the Java application are running and presence is active, switch to a different project in Resolve (e.g., "Test Project 2").
    *   **Expected:**
        *   Within the polling interval, the application logs should detect the window title change.
        *   Your Discord Rich Presence should update to reflect the new project name (e.g., "Editing: Test Project 2").

5.  **Test Case 4: Resolve Running - Go to Project Manager**
    *   While Resolve and the Java application are running and presence is active, go back to the Project Manager screen in Resolve (close the current project).
    *   **Expected:**
        *   Within the polling interval, the application logs should detect the window title change (likely to just "DaVinci Resolve").
        *   Your Discord Rich Presence should update. The details might show "Editing: Project Manager" and state "In Project Manager".

6.  **Test Case 5: Close Resolve**
    *   While the Java application is running and presence is active, close DaVinci Resolve completely.
    *   **Expected:**
        *   Within the polling interval, the application logs should show that Resolve is no longer detected.
        *   Your Discord Rich Presence for DaVinci Resolve should be cleared/disappear.

7.  **Test Case 6: Close Java Application (while Resolve is running)**
    *   Start DaVinci Resolve and open a project so that Rich Presence is active.
    *   Close the Java application (e.g., by pressing `Ctrl+C` in the terminal).
    *   **Expected:**
        *   The application logs should show the shutdown hook being triggered and Discord RPC shutting down gracefully.
        *   Your Discord Rich Presence for DaVinci Resolve should disappear.

8.  **Test Case 7: Close Java Application (while Resolve is NOT running)**
    *   Ensure DaVinci Resolve is not running.
    *   Run the Java application. It should be polling.
    *   Close the Java application (e.g., by pressing `Ctrl+C`).
    *   **Expected:**
        *   The application logs should show the shutdown hook being triggered.
        *   No errors related to Discord RPC shutdown should occur (as it might not have been initialized or already cleared).

9.  **Test Case 8: Invalid/Missing Discord App ID**
    *   Temporarily unset the `DISCORD_APP_ID` environment variable (or set it to an invalid value like "INVALID_ID").
    *   Run the Java application.
    *   If it prompts, try providing an invalid ID or no ID.
    *   **Expected:**
        *   If no valid ID can be obtained, the application logs should indicate failure to initialize Discord RPC (e.g., "Failed to initialize Discord RPC", "Discord RPC Error: error 4000 - Invalid Client ID").
        *   No Rich Presence will appear.
        *   The application should either exit (if App ID is deemed critical at startup) or continue running and polling for Resolve but without Discord functionality. (Current implementation exits if no App ID is ultimately provided).
    *   Restore your correct `DISCORD_APP_ID` afterwards for further testing.

These steps cover the main lifecycle and functionality of the application. Check the logs frequently for any errors or unexpected behavior.


## Future Improvements (Optional)

-   Cross-platform support (macOS, Linux) for process detection.
-   More detailed status information (e.g., current page in Resolve - Edit, Color, Fairlight, etc. - potentially via scripting).
-   Configuration file for settings (App ID, polling interval, image keys).
-   GUI for easier management.

---

This README provides the necessary information to get started. If you encounter issues, please check the application logs first.Tool output for `create_file_with_block`:
