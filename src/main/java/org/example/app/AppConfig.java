package org.example.app;

public final class AppConfig {

    private AppConfig() {}

    /** Set {@code -Dapp.devMode=false} to hide Crash Test controls for graduation demos. Default on for coursework. */
    public static final boolean DEV_MODE =
            Boolean.parseBoolean(System.getProperty("app.devMode", "true"));
}
