package net.minecraft.server.level;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class ServerPlayer extends Player {
    public ServerGamePacketListenerImpl connection;

    protected ServerPlayer(Level level) {
        super(level);
    }

    public ServerLevel serverLevel() { throw new UnsupportedOperationException(); }
}
