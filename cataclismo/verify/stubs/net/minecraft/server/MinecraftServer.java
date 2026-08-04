package net.minecraft.server;

public abstract class MinecraftServer {
    public net.minecraft.server.players.PlayerList getPlayerList() { throw new UnsupportedOperationException(); }
    public net.minecraft.server.ServerScoreboard getScoreboard() { throw new UnsupportedOperationException(); }
    public int getTickCount() { throw new UnsupportedOperationException(); }
}
