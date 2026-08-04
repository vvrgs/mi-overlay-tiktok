package com.vvrgs.cataclysm.client;

import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.client.fx.ClientFxDirector;
import com.vvrgs.cataclysm.client.fx.FlashOverlay;
import com.vvrgs.cataclysm.client.fx.PostShaders;
import com.vvrgs.cataclysm.client.fx.ScreenShake;
import com.vvrgs.cataclysm.client.fx.SkyFxState;
import com.vvrgs.cataclysm.client.fx.SkyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos de runtime del cliente (bus de FORGE): tick del director de FX,
 * camara (shake), fog (tintes de cielo), render de cielo, post-shaders y
 * limpieza en logout/unload.
 */
@Mod.EventBusSubscriber(modid = Cataclysm.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        ClientFxDirector.tick();
        ScreenShake.tick();
        FlashOverlay.tick();
        SkyFxState.tick();
        PostShaders.tick();
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ScreenShake.apply(event);
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        SkyFxState.tintFog(event);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            SkyRenderer.render(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            PostShaders.process(event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFxDirector.clearAll();
        SkyFxState.clearAll();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientFxDirector.clearAll();
            SkyFxState.clearAll();
        }
    }

    private ClientForgeEvents() {
    }
}
