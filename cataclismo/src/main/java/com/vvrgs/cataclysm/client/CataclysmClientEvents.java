package com.vvrgs.cataclysm.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.client.fx.ClientFxDirector;
import com.vvrgs.cataclysm.client.fx.EffekManifest;
import com.vvrgs.cataclysm.client.fx.FlashOverlay;
import com.vvrgs.cataclysm.client.particle.AshParticle;
import com.vvrgs.cataclysm.client.particle.DebrisParticle;
import com.vvrgs.cataclysm.client.particle.DustParticle;
import com.vvrgs.cataclysm.client.particle.EmberParticle;
import com.vvrgs.cataclysm.client.particle.LavaGlobParticle;
import com.vvrgs.cataclysm.client.particle.PlasmaParticle;
import com.vvrgs.cataclysm.client.particle.PopFlashParticle;
import com.vvrgs.cataclysm.client.particle.RaindropParticle;
import com.vvrgs.cataclysm.client.particle.SmokeParticle;
import com.vvrgs.cataclysm.client.particle.SparkParticle;
import com.vvrgs.cataclysm.client.particle.SprayParticle;
import com.vvrgs.cataclysm.client.particle.TelegraphParticle;
import com.vvrgs.cataclysm.client.render.BolideRenderer;
import com.vvrgs.cataclysm.client.render.CataclysmRenderTypes;
import com.vvrgs.cataclysm.client.render.Geometry;
import com.vvrgs.cataclysm.client.render.ImpactorRenderer;
import com.vvrgs.cataclysm.client.render.TornadoRenderer;
import com.vvrgs.cataclysm.client.render.TsunamiWallRenderer;
import com.vvrgs.cataclysm.client.render.VolcanicBombRenderer;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModEntities;
import com.vvrgs.cataclysm.registry.ModParticles;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.Map;

/**
 * Registro de cliente (bus del MOD): particulas, renderers, capas de modelo,
 * core shaders, overlays y reload listeners.
 */
@Mod.EventBusSubscriber(modid = Cataclysm.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CataclysmClientEvents {

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ASH.get(), AshParticle.Provider::new);
        event.registerSpriteSet(ModParticles.EMBER.get(), EmberParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DUST.get(), DustParticle.Provider::new);
        event.registerSpriteSet(ModParticles.PLASMA.get(), PlasmaParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SMOKE.get(), SmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.RAINDROP.get(), RaindropParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPRAY.get(), SprayParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPARK.get(), SparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.TELEGRAPH.get(), TelegraphParticle.Provider::new);
        event.registerSpriteSet(ModParticles.LAVA_GLOB.get(), LavaGlobParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DEBRIS.get(), DebrisParticle.Provider::new);
        event.registerSpriteSet(ModParticles.POP_FLASH.get(), PopFlashParticle.Provider::new);

        // triggers de consola: /particle cataclysm:fx_<evento> dispara la
        // receta nativa completa + su effek del manifest
        for (Map.Entry<FxEvent, ?> entry : ModParticles.FX_TRIGGERS.entrySet()) {
            FxEvent fxEvent = entry.getKey();
            event.registerSpecial(ModParticles.FX_TRIGGERS.get(fxEvent).get(),
                    (type, level, x, y, z, dx, dy, dz) -> {
                        ClientFxDirector.onFxEvent(fxEvent, x, y, z,
                                dy > 0.0D ? (float) dy : 1.0F, level.random.nextInt(), (int) dx);
                        return null;
                    });
        }
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TORNADO.get(), TornadoRenderer::new);
        event.registerEntityRenderer(ModEntities.BOLIDE.get(), BolideRenderer::new);
        event.registerEntityRenderer(ModEntities.VOLCANIC_BOMB.get(), VolcanicBombRenderer::new);
        event.registerEntityRenderer(ModEntities.TSUNAMI_WALL.get(), TsunamiWallRenderer::new);
        event.registerEntityRenderer(ModEntities.IMPACTOR.get(), ImpactorRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TornadoRenderer.LAYER, Geometry::tornadoLayer);
        event.registerLayerDefinition(BolideRenderer.LAYER, Geometry::bolideLayer);
        event.registerLayerDefinition(VolcanicBombRenderer.LAYER, Geometry::bombLayer);
        event.registerLayerDefinition(TsunamiWallRenderer.LAYER, Geometry::tsunamiLayer);
        event.registerLayerDefinition(ImpactorRenderer.LAYER, Geometry::impactorLayer);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        new ResourceLocation(Cataclysm.MODID, "rendertype_cataclysm_scroll"),
                        DefaultVertexFormat.NEW_ENTITY),
                CataclysmRenderTypes::setScrollShader);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(EffekManifest.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("cataclysm_flash", (gui, graphics, partialTick, width, height) ->
                FlashOverlay.render(graphics, width, height));
    }

    private CataclysmClientEvents() {
    }
}
