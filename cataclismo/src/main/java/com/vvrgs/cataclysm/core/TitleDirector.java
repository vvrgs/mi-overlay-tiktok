package com.vvrgs.cataclysm.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Titulos legibles entre el caos: maximo 1 titulo cada 3 s por jugador;
 * si no toca, degrada a actionbar (anti-pisado). Mapa con poda al desconectar.
 */
public final class TitleDirector {

    private static final int TITLE_COOLDOWN_TICKS = 60;

    private static final Map<UUID, Integer> LAST_TITLE_TICK = new HashMap<>();

    public static void title(ServerPlayer player, Component title, Component subtitle) {
        int now = player.server.getTickCount();
        Integer last = LAST_TITLE_TICK.get(player.getUUID());
        if (last == null || now - last >= TITLE_COOLDOWN_TICKS) {
            LAST_TITLE_TICK.put(player.getUUID(), now);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 45, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        } else {
            // degradar a actionbar: que se lea, no que se pise
            player.displayClientMessage(title, true);
        }
    }

    public static void actionbar(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    public static void forget(UUID player) {
        LAST_TITLE_TICK.remove(player);
    }

    public static void clearAll() {
        LAST_TITLE_TICK.clear();
    }

    private TitleDirector() {
    }
}
