package com.vvrgs.cataclysm.network;

import com.vvrgs.cataclysm.fx.SkyFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C global: activa/actualiza un efecto de cielo (impactor creciendo,
 * tintes, oscurecimiento, agujero negro). duration en ticks; con SkyFx.CLEAR
 * o duration<=0 se apaga.
 */
public final class SkyFxPacket {

    public final SkyFx type;
    public final int durationTicks;
    public final float intensity;

    public SkyFxPacket(SkyFx type, int durationTicks, float intensity) {
        this.type = type;
        this.durationTicks = durationTicks;
        this.intensity = intensity;
    }

    public static void encode(SkyFxPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.type.ordinal());
        buf.writeVarInt(msg.durationTicks);
        buf.writeFloat(msg.intensity);
    }

    public static SkyFxPacket decode(FriendlyByteBuf buf) {
        return new SkyFxPacket(SkyFx.byOrdinal(buf.readVarInt()), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(SkyFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHooks.handleSkyFx(msg)));
        ctx.get().setPacketHandled(true);
    }
}
