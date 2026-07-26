package net.minecraft.util;

public final class Mth {
    public static final float PI = (float) Math.PI;
    public static final float DEG_TO_RAD = ((float) Math.PI / 180.0F);
    public static final float RAD_TO_DEG = (180.0F / (float) Math.PI);

    public static float sin(float value) { return 0.0F; }
    public static float cos(float value) { return 0.0F; }
    public static float sqrt(float value) { return 0.0F; }
    public static double atan2(double y, double x) { return 0.0D; }
    public static int clamp(int value, int min, int max) { return 0; }
    public static float clamp(float value, float min, float max) { return 0.0F; }
    public static double clamp(double value, double min, double max) { return 0.0D; }
    public static float lerp(float delta, float start, float end) { return 0.0F; }
    public static double lerp(double delta, double start, double end) { return 0.0D; }
    public static float rotLerp(float delta, float start, float end) { return 0.0F; }
    public static float wrapDegrees(float value) { return 0.0F; }
    public static double wrapDegrees(double value) { return 0.0D; }
    public static int floor(double value) { return 0; }

    private Mth() {}
}
