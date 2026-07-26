package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;

public class ClientboundSetTitlesAnimationPacket implements Packet<ClientGamePacketListener> {
    public ClientboundSetTitlesAnimationPacket(int fadeIn, int stay, int fadeOut) {}
}
