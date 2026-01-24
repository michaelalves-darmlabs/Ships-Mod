package com.michallves.ships.utils;

public final class ShipMath {

    private ShipMath() {}

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float wrapDegrees(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    public static int floorMod(int a, int b) {
        int m = a % b;
        return (m < 0) ? (m + b) : m;
    }
}
