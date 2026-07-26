package com.vvrgs.irontempest.client;

import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.client.fx.FlashOverlay;
import com.vvrgs.irontempest.client.fx.FxDirector;
import com.vvrgs.irontempest.client.fx.PostFxManager;
import com.vvrgs.irontempest.client.fx.ScreenShake;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Wiring de eventos de cliente (bus FORGE). */
@Mod.EventBusSubscriber(modid = IronTempest.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        FxDirector.tick();
        ScreenShake.tick();
        FlashOverlay.tick();
        PostFxManager.tick();
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ScreenShake.apply(event);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        FlashOverlay.render(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        PostFxManager.onRenderStage(event);
    }

    /** Salir del mundo: soltar TODO estado de FX (lambdas capturan el nivel viejo). */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetAllFx();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            resetAllFx();
        }
    }

    private static void resetAllFx() {
        FxDirector.clear();
        PostFxManager.clearWaves();
        ScreenShake.reset();
        FlashOverlay.reset();
    }

    private ClientForgeEvents() {}
}
