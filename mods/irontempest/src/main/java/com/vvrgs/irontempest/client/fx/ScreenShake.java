package com.vvrgs.irontempest.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Shake de cámara por acumulador de trauma (patrón game-feel estándar:
 * shake = trauma², ruido suave incoherente por eje, decaimiento lineal).
 */
public final class ScreenShake {

    private static final float DECAY = 0.030F;
    private static final float MAX_YAW = 5.5F;
    private static final float MAX_PITCH = 4.0F;
    private static final float MAX_ROLL = 2.8F;
    private static final double FULL_DIST = 12.0D;
    private static final double ZERO_DIST = 85.0D;

    private static float trauma;
    private static float traumaO;

    /** Añade trauma con falloff por distancia de la cámara a la fuente. */
    public static void addTrauma(float amount, Vec3 source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) {
            return;
        }
        double dist = mc.gameRenderer.getMainCamera().getPosition().distanceTo(source);
        float falloff = dist <= FULL_DIST ? 1.0F
                : (float) Math.max(0.0D, 1.0D - (dist - FULL_DIST) / (ZERO_DIST - FULL_DIST));
        trauma = Math.min(1.0F, trauma + amount * falloff);
    }

    /** Trauma directo sin falloff (SHAKE_ONLY global). */
    public static void addTraumaDirect(float amount) {
        trauma = Math.min(1.0F, trauma + amount);
    }

    public static void tick() {
        traumaO = trauma;
        trauma = Math.max(0.0F, trauma - DECAY);
    }

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        float pt = (float) event.getPartialTick();
        float t = Mth.lerp(pt, traumaO, trauma);
        if (t <= 0.002F) {
            return;
        }
        float shake = t * t;
        Minecraft mc = Minecraft.getInstance();
        double time = (mc.level != null ? mc.level.getGameTime() : 0L) + pt;

        event.setYaw(event.getYaw() + noise(time, 0.0D) * MAX_YAW * shake);
        event.setPitch(event.getPitch() + noise(time, 37.7D) * MAX_PITCH * shake);
        event.setRoll(event.getRoll() + noise(time, 71.3D) * MAX_ROLL * shake);
    }

    /** Ruido suave [-1,1]: senos incoherentes en 3 frecuencias. */
    private static float noise(double time, double seed) {
        return (float) (Math.sin(time * 1.83D + seed) * 0.50D
                + Math.sin(time * 3.61D + seed * 1.7D) * 0.32D
                + Math.sin(time * 6.29D + seed * 2.3D) * 0.18D);
    }

    private ScreenShake() {}
}
