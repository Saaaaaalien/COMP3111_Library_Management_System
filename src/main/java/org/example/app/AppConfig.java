package org.example.app;

public final class AppConfig {

    private AppConfig() {}

    public static final boolean DEV_MODE =
            Boolean.parseBoolean(System.getProperty("app.devMode", "false"));
}
