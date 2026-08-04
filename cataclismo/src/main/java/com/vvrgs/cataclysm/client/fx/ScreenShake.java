package com.vvrgs.cataclysm.client.fx;

import com.vvrgs.cataclysm.CataclysmConfig;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Screen shake con modelo trauma^2: el trauma se SUMA (0..1), decae linealmente,
 * y la sacudida efectiva es trauma al cuadrado — los golpes pequenos apenas se
 * notan, los grandes sacuden de verdad. Se aplica a la camara via
 * ViewportEvent.ComputeCameraAngles.
 */
public final class ScreenShake {

    private static final float DECAY_PER_TICK = 0.018F;
    private static final float MAX_YAW = 3.2F;
    private static final float MAX_PITCH = 2.6F;
    private static final float MAX_ROLL = 2.2F;

    private static float trauma;
    private static float time;

    public static void add(float amount) {
        if (!CataclysmConfig.CLIENT.screenShake.get()) {
            return;
        }
        trauma = Mth.clamp(trauma + amount, 0.0F, 1.0F);
    }

    /** Con falloff por distancia al evento. */
    public static void addAt(double eventX, double eventY, double eventZ,
                             double camX, double camY, double camZ,
                             float amount, double maxRange) {
        double dist = Math.sqrt(Math.pow(eventX - camX, 2)
                + Math.pow(eventY - camY, 2) + Math.pow(eventZ - camZ, 2));
        if (dist >= maxRange) {
            return;
        }
        add(amount * (float) (1.0D - dist / maxRange));
    }

    public static void tick() {
        trauma = Math.max(0.0F, trauma - DECAY_PER_TICK);
        // wrap: un float que crece para siempre satura y congela el ruido
        time = (time + 1.0F) % 100000.0F;
    }

    public static void apply(ViewportEvent.ComputeCameraAngles event) {
        if (trauma <= 0.0F) {
            return;
        }
        float shake = trauma * trauma;
        float t = time + (float) event.getPartialTick();
        // ruido barato de senos desfasados: suficientemente organico a 60 fps
        float nYaw = Mth.sin(t * 2.13F) * 0.55F + Mth.sin(t * 3.71F + 1.3F) * 0.45F;
        float nPitch = Mth.sin(t * 1.87F + 4.2F) * 0.55F + Mth.sin(t * 4.13F + 0.7F) * 0.45F;
        float nRoll = Mth.sin(t * 2.71F + 2.1F) * 0.6F + Mth.sin(t * 3.17F + 5.0F) * 0.4F;
        event.setYaw(event.getYaw() + nYaw * MAX_YAW * shake);
        event.setPitch(event.getPitch() + nPitch * MAX_PITCH * shake);
        event.setRoll(event.getRoll() + nRoll * MAX_ROLL * shake);
    }

    public static void clear() {
        trauma = 0.0F;
    }

    private ScreenShake() {
    }
}
