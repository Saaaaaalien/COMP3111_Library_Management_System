package org.example.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility to set up application resources on first run
 */
public class ResourceSetup {
    public static void ensureIconExists() {
        String resourceDir = "src/main/resources/icons";
        String iconPath = resourceDir + "/app-icon.png";

        try {
            // Create icons directory if it doesn't exist
            Files.createDirectories(Paths.get(resourceDir));
            System.out.println("Icons directory ready at: " + resourceDir);
        } catch (Exception e) {
            System.err.println("Failed to create icons directory: " + e.getMessage());
        }
    }
}
