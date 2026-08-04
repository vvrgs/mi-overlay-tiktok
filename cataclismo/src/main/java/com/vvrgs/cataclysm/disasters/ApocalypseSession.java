package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.DisasterManager;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.fx.SkyFx;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

/**
 * /apocalipsis — tier U. El armagedon natural: un DIRECTOR de oleadas que
 * ENCADENA los desastres existentes con timeline exacto. Reutiliza las
 * sesiones reales (forceStart), no reimplementa nada.
 *
 * Timeline:
 *   T+0     sirena global + titulo + agujero negro en el cielo
 *   T+100   METEOROS x20      (oleada 1)
 *   T+500   RAYO x10          (oleada 2)
 *   T+900   TORNADO           (oleada 3)
 *   T+1500  TERREMOTO         (oleada 4)
 *   T+2100  VOLCAN            (oleada 5)
 *   T+3000  IMPACTO final     (oleada 6)
 */
public class ApocalypseSession extends DisasterSession {

    private record Wave(int tick, Disasters kind, int spamCount, String stageKey) {
    }

    private static final Wave[] TIMELINE = {
            new Wave(100, Disasters.METEOROS, 20, "cataclysm.apocalipsis.stage.meteoros"),
            new Wave(500, Disasters.RAYO, 10, "cataclysm.apocalipsis.stage.rayos"),
            new Wave(900, Disasters.TORNADO, 0, "cataclysm.apocalipsis.stage.tornado"),
            new Wave(1500, Disasters.TERREMOTO, 0, "cataclysm.apocalipsis.stage.terremoto"),
            new Wave(2100, Disasters.VOLCAN, 0, "cataclysm.apocalipsis.stage.volcan"),
            new Wave(3000, Disasters.IMPACTO, 0, "cataclysm.apocalipsis.stage.impacto"),
    };

    private static final int DURATION = 3400;

    private int nextWave;

    public ApocalypseSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        createBossBar(Component.translatable(kind().bossbarKey()),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6, true);
    }

    @Override
    public Disasters kind() {
        return Disasters.APOCALIPSIS;
    }

    @Override
    protected int maxLifetimeTicks() {
        return DURATION + 200;
    }

    @Override
    protected void onTick(ServerPlayer target) {
        if (age == 1) {
            if (CataclysmConfig.COMMON.broadcastMessages.get()) {
                FxDirector.globalSound(server, ModSounds.SIREN.get(), 1.0F, 0.8F);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    TitleDirector.title(player,
                            Component.translatable("cataclysm.apocalipsis.global.title"),
                            Component.translatable("cataclysm.apocalipsis.global.subtitle"));
                }
            }
            // el presagio: agujero negro con disco de acrecion sobre el mundo
            FxDirector.sky(server, SkyFx.BLACK_HOLE, DURATION, 1.0F);
            FxDirector.sky(server, SkyFx.RED_TINT, 600, 0.4F);
            FxDirector.fire(level, FxEvent.APOCALYPSE_OMEN, target.position(), 1.5F);
        }

        if (bossBar != null) {
            bossBar.setProgress(Mth.clamp((float) age / DURATION, 0.0F, 1.0F));
        }

        // lanzar oleadas del timeline
        while (nextWave < TIMELINE.length && age >= TIMELINE[nextWave].tick()) {
            Wave wave = TIMELINE[nextWave++];
            launchWave(target, wave);
        }

        if (age >= DURATION) {
            end("finished");
        }
    }

    private void launchWave(ServerPlayer target, Wave wave) {
        if (bossBar != null) {
            bossBar.setName(Component.translatable(wave.stageKey()));
        }
        TitleDirector.title(target,
                Component.translatable(wave.stageKey()),
                Component.translatable("cataclysm.apocalipsis.wave.subtitle"));
        // reutiliza las sesiones existentes tal cual. Tier S va por submit():
        // si ya hay cola spam de ese tipo para el jugador se FUSIONA en ella
        // (jamas dos colas del mismo tipo). El resto salta cupos con
        // forceStart — el apocalipsis ES el ultra activo.
        if (wave.kind().tier() == com.vvrgs.cataclysm.core.Tier.S) {
            DisasterManager.enqueueSpam(server, target, wave.kind(), wave.spamCount());
        } else {
            DisasterManager.forceStart(server, target, wave.kind());
        }
    }

    @Override
    protected void onEnd(String reason) {
        FxDirector.skyClear(server, SkyFx.BLACK_HOLE);
        FxDirector.skyClear(server, SkyFx.RED_TINT);
    }
}
