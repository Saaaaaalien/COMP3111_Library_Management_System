package org.example.util;

import java.nio.file.*;

/**
 * Copies the app icon to the resources directory
 */
public class IconSetup {
    public static void main(String[] args) throws Exception {
        String src = "C:\\Users\\savan\\AppData\\Local\\Temp\\ai-chat-attachment-3319591192196363811.png";
        String destDir = "src\\main\\resources\\icons";
        String destFile = destDir + "\\app-icon.png";

        Files.createDirectories(Paths.get(destDir));
        Files.copy(Paths.get(src), Paths.get(destFile), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Icon copied to " + destFile);
    }
}
