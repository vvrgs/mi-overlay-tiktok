package com.vvrgs.irontempest.server.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Presentación: títulos con anti-pisado (máx 1 título/3 s por jugador; si no,
 * degrada a actionbar), actionbar directa y fábrica de bossbars por sesión.
 * Con 5 misiles simultáneos el jugador ve UN título y cinco barras — legible.
 */
public final class Announcer {

    private static final long TITLE_COOLDOWN_TICKS = 60L;
    private static final Map<UUID, Long> LAST_TITLE = new HashMap<>();

    /** Título+subtítulo al objetivo; degrada a actionbar si hubo título hace <3 s. */
    public static void title(ServerPlayer player, Component title, Component subtitle) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_TITLE.get(player.getUUID());
        if (last != null && now - last < TITLE_COOLDOWN_TICKS) {
            actionbar(player, title);
            return;
        }
        LAST_TITLE.put(player.getUUID(), now);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 45, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    public static void actionbar(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    /** Bossbar de sesión. El dueño DEBE llamar removeAllPlayers() al terminar. */
    public static ServerBossEvent bar(Component name, BossEvent.BossBarColor color,
                                      BossEvent.BossBarOverlay overlay) {
        ServerBossEvent bar = new ServerBossEvent(name, color, overlay);
        bar.setProgress(0.0F);
        return bar;
    }

    /** Logout del jugador: podar su cooldown del mapa. */
    public static void removeFor(UUID player) {
        LAST_TITLE.remove(player);
    }

    /** Limpieza en parada de servidor (mapa de cooldowns entre mundos). */
    public static void clearAll() {
        LAST_TITLE.clear();
    }

    private Announcer() {}
}
