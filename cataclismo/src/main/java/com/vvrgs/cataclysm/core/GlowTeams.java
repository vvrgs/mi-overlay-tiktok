package com.vvrgs.cataclysm.core;

import com.vvrgs.cataclysm.Cataclysm;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;

/**
 * Glow outline rojo para toda entidad de desastre via scoreboard team.
 *
 * La limpieza de cada entidad va en su override de onRemovedFromWorld (NO en
 * remove(): la descarga de chunks no pasa por remove y deja UUIDs huerfanos
 * en scoreboard.dat; y setRemoved es final en 1.20.1). Ademas: purga total
 * del team en ServerStarted (restos de un crash) y en ServerStopping.
 */
public final class GlowTeams {

    private static final String TEAM_NAME = "cataclysm_glow";

    public static void register(Entity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return;
        }
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_NAME);
            team.setColor(ChatFormatting.RED);
            team.setSeeFriendlyInvisibles(false);
        }
        scoreboard.addPlayerToTeam(entity.getStringUUID(), team);
        entity.setGlowingTag(true);
    }

    public static void unregister(Entity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        MinecraftServer server = entity.getServer();
        if (server == null) {
            return;
        }
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        String key = entity.getStringUUID();
        // removePlayerFromTeam lanza IllegalStateException si la entidad no
        // esta en ESE team ("Player is either on another team or not on any
        // team. Cannot remove from team..."). Pasa de verdad: si la entidad se
        // retira antes de su primer tick de servidor nunca llego a registrarse.
        if (team != null && scoreboard.getPlayersTeam(key) == team) {
            scoreboard.removePlayerFromTeam(key, team);
        }
    }

    /** Purga total: en ServerStarted (huerfanos de crash) y ServerStopping. */
    public static void purge(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            return;
        }
        int purged = 0;
        for (String member : new ArrayList<>(team.getPlayers())) {
            scoreboard.removePlayerFromTeam(member, team);
            purged++;
        }
        if (purged > 0) {
            Cataclysm.LOGGER.info("[cataclysm] glow team purgado: {} miembros", purged);
        }
    }

    private GlowTeams() {
    }
}
