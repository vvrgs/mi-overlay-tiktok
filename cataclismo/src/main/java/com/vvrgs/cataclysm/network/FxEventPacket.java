package com.vvrgs.cataclysm.network;

import com.vvrgs.cataclysm.fx.FxEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: un evento de FX puntual. El cliente lo expande en una receta
 * cronologica (ClientFxDirector). data es un entero por-evento (radio,
 * yaw, blockStateId... documentado en FxEvent).
 */
public final class FxEventPacket {

    public final FxEvent type;
    public final double x;
    public final double y;
    public final double z;
    public final float intensity;
    public final int seed;
    public final int data;

    public FxEventPacket(FxEvent type, double x, double y, double z, float intensity, int seed, int data) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.intensity = intensity;
        this.seed = seed;
        this.data = data;
    }

    public static void encode(FxEventPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.type.ordinal());
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.intensity);
        buf.writeVarInt(msg.seed);
        buf.writeVarInt(msg.data);
    }

    public static FxEventPacket decode(FriendlyByteBuf buf) {
        return new FxEventPacket(
                FxEvent.byOrdinal(buf.readVarInt()),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    public static void handle(FxEventPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHooks.handleFxEvent(msg)));
        ctx.get().setPacketHandled(true);
    }
}
