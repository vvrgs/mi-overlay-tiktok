package com.vvrgs.irontempest.net;

import com.vvrgs.irontempest.IronTempest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(IronTempest.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Radio por defecto de difusión de FX a clientes. */
    public static final double FX_RANGE = 160.0D;

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ClientboundFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundFxPacket::encode)
                .decoder(ClientboundFxPacket::new)
                .consumerMainThread(ClientboundFxPacket::handle)
                .add();
    }

    /** Difunde un FX a todos los clientes cerca de la posición. */
    public static void fx(ServerLevel level, FxType type, Vec3 pos, Vec3 dir, float scale) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        pos.x, pos.y, pos.z, FX_RANGE, level.dimension())),
                new ClientboundFxPacket(type, pos, dir, scale));
    }

    public static void fx(ServerLevel level, FxType type, Vec3 pos, float scale) {
        fx(level, type, pos, Vec3.ZERO, scale);
    }

    private ModNetwork() {}
}
