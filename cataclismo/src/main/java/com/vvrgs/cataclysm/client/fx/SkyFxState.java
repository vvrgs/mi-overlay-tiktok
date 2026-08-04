package com.vvrgs.cataclysm.client.fx;

import com.vvrgs.cataclysm.fx.SkyFx;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * Estado cliente de los efectos de cielo. Cada uno tiene duracion e
 * intensidad; el render (SkyRenderer) y los tintes (fog + overlay) leen
 * de aqui. CLEAR o duracion 0 apaga.
 */
public final class SkyFxState {

    private static final class State {
        int ticksLeft;
        int duration;
        float intensity;
    }

    private static final Map<SkyFx, State> ACTIVE = new EnumMap<>(SkyFx.class);

    public static void apply(SkyFx type, int durationTicks, float intensity) {
        if (type == SkyFx.CLEAR) {
            clearAll();
            return;
        }
        if (durationTicks <= 0) {
            ACTIVE.remove(type);
            return;
        }
        State state = ACTIVE.computeIfAbsent(type, k -> new State());
        state.ticksLeft = durationTicks;
        state.duration = durationTicks;
        state.intensity = intensity;
    }

    public static void tick() {
        ACTIVE.entrySet().removeIf(entry -> --entry.getValue().ticksLeft <= 0);

        // alimentar el tinte de pantalla del efecto dominante
        if (isActive(SkyFx.ASH_DARKEN)) {
            FlashOverlay.setTint(0x2B2B30, 0.45F * intensity(SkyFx.ASH_DARKEN));
        } else if (isActive(SkyFx.EYE_SEPIA)) {
            FlashOverlay.setTint(0xC8A548, 0.28F * intensity(SkyFx.EYE_SEPIA));
        } else if (isActive(SkyFx.RED_TINT)) {
            FlashOverlay.setTint(0x7A1010, 0.30F * intensity(SkyFx.RED_TINT));
        }
    }

    public static boolean isActive(SkyFx type) {
        return ACTIVE.containsKey(type);
    }

    public static float intensity(SkyFx type) {
        State state = ACTIVE.get(type);
        return state == null ? 0.0F : state.intensity;
    }

    /** Progreso 0..1 del efecto (fraccion transcurrida de su duracion). */
    public static float progress(SkyFx type) {
        State state = ACTIVE.get(type);
        if (state == null || state.duration <= 0) {
            return 0.0F;
        }
        return Mth.clamp(1.0F - (float) state.ticksLeft / state.duration, 0.0F, 1.0F);
    }

    /** Tinta la niebla/horizonte segun los efectos activos. */
    public static void tintFog(ViewportEvent.ComputeFogColor event) {
        if (isActive(SkyFx.ASH_DARKEN)) {
            float f = 0.6F * intensity(SkyFx.ASH_DARKEN);
            event.setRed(Mth.lerp(f, event.getRed(), 0.18F));
            event.setGreen(Mth.lerp(f, event.getGreen(), 0.18F));
            event.setBlue(Mth.lerp(f, event.getBlue(), 0.20F));
        }
        if (isActive(SkyFx.RED_TINT)) {
            float f = 0.5F * intensity(SkyFx.RED_TINT);
            event.setRed(Mth.lerp(f, event.getRed(), 0.55F));
            event.setGreen(Mth.lerp(f, event.getGreen(), 0.12F));
            event.setBlue(Mth.lerp(f, event.getBlue(), 0.10F));
        }
        if (isActive(SkyFx.EYE_SEPIA)) {
            float f = 0.5F * intensity(SkyFx.EYE_SEPIA);
            event.setRed(Mth.lerp(f, event.getRed(), 0.80F));
            event.setGreen(Mth.lerp(f, event.getGreen(), 0.66F));
            event.setBlue(Mth.lerp(f, event.getBlue(), 0.35F));
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    private SkyFxState() {
    }
}
