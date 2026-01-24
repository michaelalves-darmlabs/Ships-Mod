package com.michallves.ships.utils;

public final class ShipLogger {

    private ShipLogger() {}

    private static final String PREFIX = "[Ships] ";

    private static final String RESET  = "\u001B[0m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GRAY   = "\u001B[90m";

    public static void info(String message) {
        System.out.println(PREFIX + CYAN + message + RESET);
    }

    public static void success(String message) {
        System.out.println(PREFIX + GREEN + "[SUCCESS] " + message + RESET);
    }

    public static void error(String message) {
        System.out.println(PREFIX + RED + "[ERROR] " + message + RESET);
    }

    public static void warn(String message) {
        System.out.println(PREFIX + YELLOW + "[WARN] " + message + RESET);
    }

    public static void debug(String message) {
        System.out.println(PREFIX + GRAY + "[DEBUG] " + message + RESET);
    }
}
