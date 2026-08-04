package com.vvrgs.cataclysm.network;

import com.vvrgs.cataclysm.client.fx.ClientFxDirector;
import com.vvrgs.cataclysm.client.fx.SkyFxState;

/**
 * Frontera server/cliente: SOLO se invoca via DistExecutor en Dist.CLIENT.
 * Mantiene las clases de cliente fuera del classloading del server dedicado.
 */
final class ClientPacketHooks {

    static void handleFxEvent(FxEventPacket msg) {
        ClientFxDirector.onFxEvent(msg.type, msg.x, msg.y, msg.z, msg.intensity, msg.seed, msg.data);
    }

    static void handleSkyFx(SkyFxPacket msg) {
        SkyFxState.apply(msg.type, msg.durationTicks, msg.intensity);
    }

    static void handleClear() {
        ClientFxDirector.clearAll();
        SkyFxState.clearAll();
    }

    private ClientPacketHooks() {
    }
}
