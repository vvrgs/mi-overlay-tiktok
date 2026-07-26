package com.vvrgs.irontempest.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.vvrgs.irontempest.IronTempest;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * RenderTypes propios del mod. Extiende {@link RenderStateShard} (patrón
 * estándar de mods) para poder usar los shards protected static de vanilla
 * (LIGHTNING_TRANSPARENCY = blend aditivo, NO_CULL, COLOR_WRITE) y las
 * clases anidadas protected (ShaderStateShard, TextureStateShard).
 */
public final class ModRenderTypes extends RenderStateShard {

    private static final ResourceLocation BEAM_TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/misc/beam.png");
    private static final ResourceLocation WHITE_TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/misc/white.png");

    /** Shader core registrado en ClientSetup.onRegisterShaders. */
    private static ShaderInstance energyBeamShader;

    public static void setEnergyBeamShader(ShaderInstance shader) {
        energyBeamShader = shader;
    }

    public static ShaderInstance getEnergyBeamShader() {
        return energyBeamShader;
    }

    /**
     * Haz de energía del warship: POSITION_COLOR_TEX, shader propio con
     * doble scroll por GameTime, textura beam.png (blur, sin mipmap),
     * blend aditivo, sin escritura de depth, sin culling.
     */
    public static final RenderType ENERGY_BEAM = RenderType.create(
            "irontempest_energy_beam",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(ModRenderTypes::getEnergyBeamShader))
                    .setTextureState(new TextureStateShard(BEAM_TEXTURE, true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET) // Fabulous: no quedar detrás del translúcido
                    .createCompositeState(false));

    /**
     * Quads aditivos genéricos (designador láser, trazadores, flashes):
     * shader vanilla position_color_tex sobre white.png, blend aditivo.
     */
    public static final RenderType ADDITIVE_QUADS = RenderType.create(
            "irontempest_additive_quads",
            DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorTexShader))
                    .setTextureState(new TextureStateShard(WHITE_TEXTURE, false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET) // Fabulous: no quedar detrás del translúcido
                    .createCompositeState(false));

    /** Capa emisiva fullbright sobre una textura de glow. */
    public static RenderType glow(ResourceLocation texture) {
        return RenderType.eyes(texture);
    }

    private ModRenderTypes(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }
}
