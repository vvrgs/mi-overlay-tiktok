package com.vvrgs.cataclysm.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * RenderTypes propios. El shader de scroll (core shader GLSL propio,
 * registrado en RegisterShadersEvent) desplaza las UVs con GameTime:
 * muros de agua y cortinas que FLUYEN de verdad, sin animar la malla.
 */
public final class CataclysmRenderTypes extends RenderStateShard {

    private static ShaderInstance scrollShader;

    public static void setScrollShader(ShaderInstance shader) {
        scrollShader = shader;
    }

    private static final ShaderStateShard SCROLL_SHADER =
            new ShaderStateShard(() -> scrollShader);

    /** Lamina con scroll de UV vertical (GameTime): el agua del tsunami fluye. */
    public static RenderType uvScroll(ResourceLocation texture) {
        return RenderType.create("cataclysm_uv_scroll",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
                RenderType.CompositeState.builder()
                        .setShaderState(SCROLL_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false));
    }

    private CataclysmRenderTypes() {
        super("", () -> {
        }, () -> {
        });
    }
}
