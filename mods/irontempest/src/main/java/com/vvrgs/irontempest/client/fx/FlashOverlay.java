package com.vvrgs.irontempest.client.fx;

import com.vvrgs.irontempest.config.WarConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Flash de pantalla breve (blanco cálido en explosiones, rojo en armagedón). */
public final class FlashOverlay {

    private static final float DECAY = 0.07F;
    private static final float MAX_ALPHA_SCALE = 0.65F; // nunca cegar del todo

    private static float alpha;
    private static float alphaO;
    private static int rgb = 0xFFF4E0;

    public static void flash(float intensity, int color) {
        if (!WarConfig.FLASH_OVERLAY.get()) {
            return;
        }
        if (intensity > alpha) {
            alpha = Math.min(1.0F, intensity);
            rgb = color & 0xFFFFFF;
        }
    }

    public static void tick() {
        alphaO = alpha;
        alpha = Math.max(0.0F, alpha - DECAY);
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        float a = Mth.lerp(partialTick, alphaO, alpha);
        if (a <= 0.01F) {
            return;
        }
        int argb = ((int) (a * MAX_ALPHA_SCALE * 255.0F) << 24) | rgb;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb);
    }

    private FlashOverlay() {}
}
