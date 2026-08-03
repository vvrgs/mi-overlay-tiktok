package net.minecraft.server.level;

import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;

public class ServerBossEvent extends BossEvent {
    public ServerBossEvent(Component name, BossBarColor color, BossBarOverlay overlay) {
        super(null, name, color, overlay);
    }

    public void addPlayer(ServerPlayer player) {}
    public void removePlayer(ServerPlayer player) {}
    public void removeAllPlayers() {}
    public Collection<ServerPlayer> getPlayers() { throw new UnsupportedOperationException(); }
    public void setVisible(boolean visible) {}
    public boolean isVisible() { return true; }
}
