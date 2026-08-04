package com.vvrgs.cataclysm.network;

import com.vvrgs.cataclysm.Cataclysm;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CataclysmNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Cataclysm.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.messageBuilder(FxEventPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FxEventPacket::encode)
                .decoder(FxEventPacket::decode)
                .consumerMainThread(FxEventPacket::handle)
                .add();
        CHANNEL.messageBuilder(SkyFxPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkyFxPacket::encode)
                .decoder(SkyFxPacket::decode)
                .consumerMainThread(SkyFxPacket::handle)
                .add();
        CHANNEL.messageBuilder(ClearFxPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClearFxPacket::encode)
                .decoder(ClearFxPacket::decode)
                .consumerMainThread(ClearFxPacket::handle)
                .add();
    }

    private CataclysmNetwork() {
    }
}
