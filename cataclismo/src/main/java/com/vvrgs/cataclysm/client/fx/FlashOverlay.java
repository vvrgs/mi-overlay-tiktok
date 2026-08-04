package com.vvrgs.cataclysm.client.fx;

import com.vvrgs.cataclysm.CataclysmConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Flash de pantalla (impactos, rayos) + tinte persistente de cielo
 * (rojo de aviso, sepia del ojo, gris de ceniza). Se dibuja como overlay
 * encima de todo con alpha decayendo.
 */
public final class FlashOverlay {

    private static float flashStrength;
    private static int flashColor = 0xFFFFFF;

    /** tinte sostenido (lo alimenta SkyFxState cada tick) */
    private static float tintStrength;
    private static int tintColor;

    public static void flash(float strength, int rgb) {
        if (!CataclysmConfig.CLIENT.flashOverlay.get()) {
            return;
        }
        if (strength > flashStrength) {
            flashStrength = Math.min(1.0F, strength);
            flashColor = rgb;
        }
    }

    /** Con falloff por distancia. */
    public static void flashAt(double ex, double ey, double ez,
                               double camX, double camY, double camZ,
                               float strength, double maxRange, int rgb) {
        double dist = Math.sqrt(Math.pow(ex - camX, 2) + Math.pow(ey - camY, 2)
                + Math.pow(ez - camZ, 2));
        if (dist >= maxRange) {
            return;
        }
        flash(strength * (float) (1.0D - dist / maxRange), rgb);
    }

    static void setTint(int rgb, float strength) {
        tintColor = rgb;
        tintStrength = Mth.clamp(strength, 0.0F, 0.6F); // el tinte jamas ciega
    }

    public static void tick() {
        flashStrength = Math.max(0.0F, flashStrength - 0.06F);
        tintStrength = Math.max(0.0F, tintStrength - 0.01F); // se re-alimenta si sigue activo
    }

    public static void render(GuiGraphics graphics, int width, int height) {
        if (tintStrength > 0.004F) {
            int alpha = (int) (tintStrength * 255.0F) << 24;
            graphics.fill(0, 0, width, height, alpha | (tintColor & 0xFFFFFF));
        }
        if (flashStrength > 0.004F) {
            int alpha = (int) (flashStrength * 255.0F) << 24;
            graphics.fill(0, 0, width, height, alpha | (flashColor & 0xFFFFFF));
        }
    }

    public static void clear() {
        flashStrength = 0.0F;
        tintStrength = 0.0F;
    }

    private FlashOverlay() {
    }
}
