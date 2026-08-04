package com.vvrgs.cataclysm.client.fx;

import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.CataclysmConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.EnumMap;
import java.util.Map;

/**
 * Post-procesado propio (pipeline de PostChain): distorsion en ondas
 * expansivas, aberracion en el impacto.
 *
 * SIEMPRE con auto-apagado si Iris/Oculus esta cargado — los post propios
 * rompen con shaderpacks. Y si un chain falla al cargar, se desactiva ese
 * efecto con UN log, jamas crashea el frame.
 */
public final class PostShaders {

    public enum Effect {
        DISTORTION(new ResourceLocation(Cataclysm.MODID, "shaders/post/distortion.json")),
        ABERRATION(new ResourceLocation(Cataclysm.MODID, "shaders/post/aberration.json"));

        final ResourceLocation location;

        Effect(ResourceLocation location) {
            this.location = location;
        }
    }

    private static final Map<Effect, PostChain> CHAINS = new EnumMap<>(Effect.class);
    private static final Map<Effect, Integer> TICKS_LEFT = new EnumMap<>(Effect.class);
    private static final Map<Effect, Boolean> BROKEN = new EnumMap<>(Effect.class);
    private static Boolean shaderpackPresent;
    private static int lastWidth;
    private static int lastHeight;

    private static boolean blocked() {
        if (shaderpackPresent == null) {
            shaderpackPresent = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
            if (shaderpackPresent) {
                Cataclysm.LOGGER.info("[cataclysm] Iris/Oculus detectado: post-shaders propios desactivados");
            }
        }
        return shaderpackPresent || !CataclysmConfig.CLIENT.postShader.get();
    }

    public static void trigger(Effect effect, int ticks) {
        if (blocked() || BROKEN.getOrDefault(effect, false)) {
            return;
        }
        TICKS_LEFT.merge(effect, ticks, Math::max);
    }

    public static void tick() {
        TICKS_LEFT.entrySet().removeIf(entry -> {
            int left = entry.getValue() - 1;
            if (left <= 0) {
                return true;
            }
            entry.setValue(left);
            return false;
        });
    }

    /** Llamado tras renderizar el nivel (RenderLevelStageEvent AFTER_LEVEL). */
    public static void process(float partialTick) {
        if (TICKS_LEFT.isEmpty() || blocked()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        for (Effect effect : TICKS_LEFT.keySet()) {
            if (BROKEN.getOrDefault(effect, false)) {
                continue;
            }
            try {
                PostChain chain = CHAINS.get(effect);
                if (chain == null) {
                    chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(),
                            mc.getMainRenderTarget(), effect.location);
                    chain.resize(width, height);
                    CHAINS.put(effect, chain);
                }
                if (width != lastWidth || height != lastHeight) {
                    chain.resize(width, height);
                }
                chain.process(partialTick);
                mc.getMainRenderTarget().bindWrite(false);
            } catch (Exception e) {
                BROKEN.put(effect, true);
                Cataclysm.LOGGER.warn("[cataclysm] post-shader {} desactivado: {}", effect, e.toString());
            }
        }
        lastWidth = width;
        lastHeight = height;
    }

    public static void stopAll() {
        TICKS_LEFT.clear();
    }

    private PostShaders() {
    }
}
