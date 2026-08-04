package com.vvrgs.cataclysm.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: /cataclysm stopall — corta TODO en el cliente: recetas encoladas,
 * shake, flashes, post-shaders y estados de cielo.
 */
public final class ClearFxPacket {

    public ClearFxPacket() {
    }

    public static void encode(ClearFxPacket msg, FriendlyByteBuf buf) {
    }

    public static ClearFxPacket decode(FriendlyByteBuf buf) {
        return new ClearFxPacket();
    }

    public static void handle(ClearFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHooks.handleClear()));
        ctx.get().setPacketHandled(true);
    }
}
