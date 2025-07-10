package com.example.resolverp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectNameExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ProjectNameExtractor.class);

    // Regex to capture the project name. Example titles:
    // "Untitled Project - DaVinci Resolve"
    // "My Awesome Project - DaVinci Resolve"
    // "Project Name With Spaces - DaVinci Resolve"
    // This regex captures everything before " - DaVinci Resolve"
    private static final Pattern RESOLVE_TITLE_PATTERN = Pattern.compile("^(.*?) - DaVinci Resolve(?: Studio)?(?: Beta)?(?: \\d+\\.\\d+)?$");
    private static final Pattern RESOLVE_TITLE_PATTERN_NO_PROJECT = Pattern.compile("^DaVinci Resolve(?: Studio)?(?: Beta)?(?: \\d+\\.\\d+)?$");


    public Optional<String> extractProjectName(String windowTitle) {
        if (windowTitle == null || windowTitle.trim().isEmpty()) {
            logger.warn("Window title is null or empty. Cannot extract project name.");
            return Optional.empty();
        }

        Matcher matcher = RESOLVE_TITLE_PATTERN.matcher(windowTitle);
        if (matcher.find()) {
            String projectName = matcher.group(1).trim();
            if (!projectName.isEmpty()) {
                logger.debug("Extracted project name: '{}' from title: '{}'", projectName, windowTitle);
                return Optional.of(projectName);
            } else {
                // This case might happen if the title is just " - DaVinci Resolve", which is unlikely.
                logger.warn("Matched Resolve title pattern, but extracted project name is empty. Title: '{}'", windowTitle);
                return Optional.of("Untitled Project"); // Default for safety, or empty.
            }
        } else {
            Matcher noProjectMatcher = RESOLVE_TITLE_PATTERN_NO_PROJECT.matcher(windowTitle);
            if (noProjectMatcher.matches()){
                 logger.debug("Window title '{}' indicates DaVinci Resolve is running but no project is open (e.g. Project Manager screen).", windowTitle);
                 return Optional.of("Project Manager"); // Or a specific state
            }
            logger.warn("Could not extract project name from window title: '{}'. Pattern did not match.", windowTitle);
            return Optional.empty();
        }
    }

    // Basic test
    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        ProjectNameExtractor extractor = new ProjectNameExtractor();

        String title1 = "My Sweet Project - DaVinci Resolve";
        String title2 = "Another Project - DaVinci Resolve Studio";
        String title3 = "Beta Test - DaVinci Resolve Beta";
        String title4 = "Versioned Project - DaVinci Resolve 18.5";
        String title5 = "Complex Name - With Hyphens - DaVinci Resolve";
        String title6 = "  Leading And Trailing Spaces Project  - DaVinci Resolve  ";
        String title7 = "DaVinci Resolve"; // No project open
        String title8 = "DaVinci Resolve Studio"; // No project open
        String title9 = "Invalid Title";
        String title10 = "DaVinci Resolve Beta";
        String title11 = "Untitled Project - DaVinci Resolve";


        System.out.println("'" + title1 + "' -> '" + extractor.extractProjectName(title1).orElse("Failed") + "'");
        System.out.println("'" + title2 + "' -> '" + extractor.extractProjectName(title2).orElse("Failed") + "'");
        System.out.println("'" + title3 + "' -> '" + extractor.extractProjectName(title3).orElse("Failed") + "'");
        System.out.println("'" + title4 + "' -> '" + extractor.extractProjectName(title4).orElse("Failed") + "'");
        System.out.println("'" + title5 + "' -> '" + extractor.extractProjectName(title5).orElse("Failed") + "'");
        System.out.println("'" + title6 + "' -> '" + extractor.extractProjectName(title6).orElse("Failed") + "'");
        System.out.println("'" + title7 + "' -> '" + extractor.extractProjectName(title7).orElse("Failed") + "' (Expected: Project Manager)");
        System.out.println("'" + title8 + "' -> '" + extractor.extractProjectName(title8).orElse("Failed") + "' (Expected: Project Manager)");
        System.out.println("'" + title9 + "' -> '" + extractor.extractProjectName(title9).orElse("Failed") + "' (Expected: Failed)");
        System.out.println("'" + title10 + "' -> '" + extractor.extractProjectName(title10).orElse("Failed") + "' (Expected: Project Manager)");
        System.out.println("'" + title11 + "' -> '" + extractor.extractProjectName(title11).orElse("Failed") + "' (Expected: Untitled Project)");
    }
}
