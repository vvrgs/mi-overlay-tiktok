package com.vvrgs.irontempest.server.util;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

/**
 * VISIBILIDAD: todas las máquinas de guerra llevan glowing tag + equipo rojo de
 * scoreboard → contorno ROJO visible a través de paredes y del caos de FX de
 * otros mods. Team idempotente; las membresías se limpian al descartar.
 */
public final class WarTeam {

    private static final String TEAM_NAME = "irontempest";

    private static PlayerTeam ensure(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_NAME);
            team.setColor(ChatFormatting.RED);
        }
        return team;
    }

    /** Marca la entidad: contorno rojo brillante (server-side, ver-a-través-de-paredes). */
    public static void join(Entity entity) {
        MinecraftServer server = entity.level().getServer();
        if (server == null) {
            return;
        }
        entity.setGlowingTag(true);
        server.getScoreboard().addPlayerToTeam(entity.getStringUUID(), ensure(server));
    }

    /** Limpieza de la membresía (llamar desde setRemoved() de la entidad). */
    public static void leave(Entity entity) {
        MinecraftServer server = entity.level().getServer();
        if (server == null) {
            return;
        }
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        // Patrón seguro: solo quitar si sigue siendo miembro de ESTE equipo
        // (plugins de scoreboard de Mohist pueden haberlo movido).
        if (team != null && scoreboard.getPlayersTeam(entity.getStringUUID()) == team) {
            scoreboard.removePlayerFromTeam(entity.getStringUUID(), team);
        }
    }

    /** Purga TOTAL del equipo (arranque y parada del servidor): barre las
     *  membresías huérfanas que un crash o una descarga de chunk hayan dejado
     *  persistidas en scoreboard.dat — sin esto crecen entre lives para
     *  siempre y vanilla manda la lista completa a cada jugador que entra. */
    public static void purge(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team != null) {
            scoreboard.removePlayerTeam(team); // ensure() lo recrea bajo demanda
        }
    }

    private WarTeam() {}
}
