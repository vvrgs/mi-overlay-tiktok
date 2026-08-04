package com.vvrgs.cataclysm.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * RenderTypes propios. Se extiende RenderType (patron estandar de mods) para
 * acceder a los shards protegidos. El shader de scroll (core shader GLSL
 * propio, registrado en RegisterShadersEvent) desplaza las UVs con GameTime:
 * muros de agua y cortinas que FLUYEN de verdad, sin animar la malla.
 */
public final class CataclysmRenderTypes extends RenderType {

    private static ShaderInstance scrollShader;

    public static void setScrollShader(ShaderInstance shader) {
        scrollShader = shader;
    }

    private static final ShaderStateShard SCROLL_SHADER =
            new ShaderStateShard(() -> scrollShader);

    /** memoizado: un RenderType nuevo por frame romperia el batching */
    private static final Map<ResourceLocation, RenderType> UV_SCROLL_CACHE = new HashMap<>();

    /** Lamina con scroll de UV vertical (GameTime): el agua del tsunami fluye. */
    public static RenderType uvScroll(ResourceLocation texture) {
        return UV_SCROLL_CACHE.computeIfAbsent(texture, tex -> create("cataclysm_uv_scroll",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
                RenderType.CompositeState.builder()
                        .setShaderState(SCROLL_SHADER)
                        .setTextureState(new TextureStateShard(tex, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(false)));
    }

    /** No instanciable: solo hereda para alcanzar los shards protegidos. */
    private CataclysmRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                                 int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                 Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
