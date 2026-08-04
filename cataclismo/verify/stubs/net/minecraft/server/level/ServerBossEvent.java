package net.minecraft.server.level;

public class ServerBossEvent extends net.minecraft.world.BossEvent {
    public ServerBossEvent(net.minecraft.network.chat.Component name, BossBarColor color, BossBarOverlay overlay) {}
    public void addPlayer(ServerPlayer player) {}
    public void removePlayer(ServerPlayer player) {}
    public void removeAllPlayers() {}
    public java.util.Collection<ServerPlayer> getPlayers() { throw new UnsupportedOperationException(); }
    public void setVisible(boolean visible) {}
}
