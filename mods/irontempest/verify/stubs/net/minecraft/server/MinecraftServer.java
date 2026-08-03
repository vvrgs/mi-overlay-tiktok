package net.minecraft.server;

import net.minecraft.server.players.PlayerList;

public abstract class MinecraftServer {
    public PlayerList getPlayerList() { throw new UnsupportedOperationException(); }
    public ServerScoreboard getScoreboard() { throw new UnsupportedOperationException(); }
}
