package com.vvrgs.irontempest.client;

import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.client.model.geom.CruiseMissileGeometry;
import com.vvrgs.irontempest.client.model.geom.MlrsRocketGeometry;
import com.vvrgs.irontempest.client.model.geom.TankGeometry;
import com.vvrgs.irontempest.client.model.geom.TankShellGeometry;
import com.vvrgs.irontempest.client.model.geom.WarshipGeometry;
import com.vvrgs.irontempest.client.particle.ChargeMoteParticle;
import com.vvrgs.irontempest.client.particle.DebrisParticle;
import com.vvrgs.irontempest.client.particle.EmberParticle;
import com.vvrgs.irontempest.client.particle.FireballParticle;
import com.vvrgs.irontempest.client.particle.FlashParticle;
import com.vvrgs.irontempest.client.particle.MuzzleFlashParticle;
import com.vvrgs.irontempest.client.particle.ShockwaveParticle;
import com.vvrgs.irontempest.client.particle.SmokeParticle;
import com.vvrgs.irontempest.client.particle.SparkParticle;
import com.vvrgs.irontempest.client.particle.TracerParticle;
import com.vvrgs.irontempest.client.particle.WarpFlashParticle;
import com.vvrgs.irontempest.client.render.CruiseMissileRenderer;
import com.vvrgs.irontempest.client.render.MlrsRocketRenderer;
import com.vvrgs.irontempest.client.render.ModRenderTypes;
import com.vvrgs.irontempest.client.render.TankRenderer;
import com.vvrgs.irontempest.client.render.TankShellRenderer;
import com.vvrgs.irontempest.client.render.WarshipRenderer;
import com.vvrgs.irontempest.registry.ModEntities;
import com.vvrgs.irontempest.registry.ModParticles;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = IronTempest.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WAR_TANK.get(), TankRenderer::new);
        event.registerEntityRenderer(ModEntities.TANK_SHELL.get(), TankShellRenderer::new);
        event.registerEntityRenderer(ModEntities.CRUISE_MISSILE.get(), CruiseMissileRenderer::new);
        event.registerEntityRenderer(ModEntities.MLRS_ROCKET.get(), MlrsRocketRenderer::new);
        event.registerEntityRenderer(ModEntities.WARSHIP.get(), WarshipRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.WAR_TANK, TankGeometry::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.TANK_SHELL, TankShellGeometry::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.CRUISE_MISSILE, CruiseMissileGeometry::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.MLRS_ROCKET, MlrsRocketGeometry::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.WARSHIP, WarshipGeometry::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FIREBALL.get(), FireballParticle.Provider::new);
        event.registerSpriteSet(ModParticles.FLASH.get(), FlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SHOCKWAVE.get(), ShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SMOKE.get(), SmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SPARK.get(), SparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DEBRIS.get(), DebrisParticle.Provider::new);
        event.registerSpriteSet(ModParticles.TRACER.get(), TracerParticle.Provider::new);
        event.registerSpriteSet(ModParticles.MUZZLE_FLASH.get(), MuzzleFlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CHARGE_MOTE.get(), ChargeMoteParticle.Provider::new);
        event.registerSpriteSet(ModParticles.WARP_FLASH.get(), WarpFlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.EMBER.get(), EmberParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        new ResourceLocation(IronTempest.MODID, "rendertype_energy_beam"),
                        DefaultVertexFormat.POSITION_COLOR_TEX),
                ModRenderTypes::setEnergyBeamShader);
    }

    private ClientSetup() {}
}
