package net.minecraft.server.players;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public abstract class PlayerList {
    public List<ServerPlayer> getPlayers() { throw new UnsupportedOperationException(); }

    @Nullable
    public ServerPlayer getPlayer(UUID id) { return null; }

    public void broadcastSystemMessage(Component message, boolean overlay) {}
}
