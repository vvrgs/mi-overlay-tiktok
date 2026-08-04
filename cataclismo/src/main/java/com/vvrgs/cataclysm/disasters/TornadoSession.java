package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.Anchors;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.core.TotemShredder;
import com.vvrgs.cataclysm.entity.TornadoEntity;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModEntities;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

/**
 * /tornado — tier C (cinematico).
 *
 * Coreografia (45 s):
 *   T+0..60    NACIMIENTO: el embudo aparece a ~40 bl y crece (intensidad 0->1)
 *   T+60..840  CAZA: persigue al jugador a 0.4 b/t, succion fisica r=12,
 *              arranca bloques que orbitan, lanza al cielo si atrapa
 *   T+840..900 MUERTE: el embudo se deshace en escombros
 */
public class TornadoSession extends DisasterSession {

    private static final int BIRTH_END = 60;
    private static final int DECAY_START = 840;
    private static final int DURATION = 900;

    private TornadoEntity tornado;
    private boolean launchRewarded;

    public TornadoSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
        createBossBar(Component.translatable(kind().bossbarKey()),
                BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS, false);
    }

    @Override
    public Disasters kind() {
        return Disasters.TORNADO;
    }

    @Override
    protected int maxLifetimeTicks() {
        return DURATION + 60;
    }

    @Override
    protected void onTick(ServerPlayer target) {
        if (age >= DURATION) {
            end("finished");
            return;
        }
        if (bossBar != null) {
            bossBar.setProgress(1.0F - (float) age / DURATION);
        }

        if (tornado == null || !tornado.isAlive()) {
            if (age < DECAY_START) {
                spawnFunnel(target, 40.0D);
            } else {
                end("funnel_lost");
                return;
            }
        }

        // intensidad por fase
        float intensity;
        if (age < BIRTH_END) {
            intensity = (float) age / BIRTH_END;
        } else if (age >= DECAY_START) {
            intensity = 1.0F - (float) (age - DECAY_START) / (DURATION - DECAY_START);
        } else {
            intensity = 1.0F;
        }
        tornado.setIntensity(intensity);
        tornado.setChaseTarget(target.position());
        tornado.setLifeTicks(200); // la sesion lo mantiene vivo; sin sesion, muere solo

        // el tren del tornado: loop sin costura re-disparado
        if (age % 35 == 0) {
            FxDirector.sound(level, tornado.position(), ModSounds.TORNADO_LOOP.get(),
                    3.2F, 0.95F + random.nextFloat() * 0.1F);
        }

        // recompensa de captura: lanzado al cielo => pops
        if (!launchRewarded && target.getDeltaMovement().y > 1.8D
                && target.position().distanceTo(tornado.position()) < 6.0D) {
            launchRewarded = true;
            TotemShredder.start(target, kind().commandName(), 3,
                    CataclysmConfig.COMMON.shredIntervalTicks.get());
            TitleDirector.actionbar(target, Component.translatable("cataclysm.tornado.caught"));
        }
        if (launchRewarded && target.onGround()) {
            launchRewarded = false; // puede volver a atraparlo
        }
    }

    private void spawnFunnel(ServerPlayer target, double distance) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double x = target.getX() + Math.cos(angle) * distance;
        double z = target.getZ() + Math.sin(angle) * distance;
        double y = Anchors.surfaceY(level, Mth.floor(x), Mth.floor(z));

        TornadoEntity entity = new TornadoEntity(ModEntities.TORNADO.get(), level);
        // estado sincronizado ANTES de addFreshEntity: viaja en el primer packet
        entity.setPos(x, y, z);
        entity.setChaseTarget(target.position());
        entity.setIntensity(age < BIRTH_END ? 0.05F : 1.0F);
        entity.setLifeTicks(200);
        level.addFreshEntity(entity);
        this.tornado = entity;
        FxDirector.fire(level, FxEvent.WIND_GUST, new Vec3(x, y, z), 1.0F,
                (int) Math.toDegrees(angle));
    }

    @Override
    protected void onReanchor(ServerPlayer target, boolean dimensionChange) {
        // discard + respawn con FX de entrada: se lee intencional.
        // NUNCA teleportTo cross-dim (recreacion limpia via spawn nuevo)
        if (tornado != null) {
            tornado.discard();
            tornado = null;
        }
        spawnFunnel(target, 24.0D);
    }

    @Override
    protected void onEnd(String reason) {
        if (tornado != null) {
            // escombros al morir
            FxDirector.fire(level, FxEvent.TORNADO_DEBRIS, tornado.position(), 1.0F, 0);
            tornado.discard();
            tornado = null;
        }
    }
}
