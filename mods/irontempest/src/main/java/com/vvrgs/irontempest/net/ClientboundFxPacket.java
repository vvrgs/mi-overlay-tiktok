package com.vvrgs.irontempest.net;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** S→C: dispara una composición de FX en el cliente (ver FxDirector). */
public record ClientboundFxPacket(FxType type, double x, double y, double z,
                                  float dx, float dy, float dz, float scale) {

    public ClientboundFxPacket(FxType type, Vec3 pos, Vec3 dir, float scale) {
        this(type, pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z, scale);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(type.ordinal());
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(dx);
        buf.writeFloat(dy);
        buf.writeFloat(dz);
        buf.writeFloat(scale);
    }

    public ClientboundFxPacket(FriendlyByteBuf buf) {
        this(FxType.VALUES[buf.readByte()],
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // consumerMainThread ya nos deja en el hilo principal del cliente.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.vvrgs.irontempest.client.fx.FxDirector.handlePacket(this));
        ctx.get().setPacketHandled(true);
    }
}
