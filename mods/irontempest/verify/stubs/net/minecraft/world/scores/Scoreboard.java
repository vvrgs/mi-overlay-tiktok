package net.minecraft.world.scores;

import org.jetbrains.annotations.Nullable;

public class Scoreboard {
    @Nullable
    public PlayerTeam getPlayerTeam(String name) { throw new UnsupportedOperationException(); }

    public PlayerTeam addPlayerTeam(String name) { throw new UnsupportedOperationException(); }

    public void removePlayerTeam(PlayerTeam team) {}

    public boolean addPlayerToTeam(String username, PlayerTeam team) { return false; }

    public void removePlayerFromTeam(String username, PlayerTeam team) {}

    @Nullable
    public PlayerTeam getPlayersTeam(String username) { throw new UnsupportedOperationException(); }
}
