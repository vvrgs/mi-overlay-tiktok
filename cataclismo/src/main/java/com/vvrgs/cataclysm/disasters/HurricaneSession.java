package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.Physics;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.core.TotemShredder;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.fx.SkyFx;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * /huracan — tier C. Huracan con OJO.
 *
 * Coreografia (75 s):
 *   T+0..600     PRIMERA PARED: bandas de lluvia + viento fisico creciente
 *                (empuje lateral sostenido con rafagas)
 *   T+600..900   EL OJO: 15 s de calma sobrecogedora — cielo amarillo,
 *                SILENCIO TOTAL. El contraste ES el efecto.
 *   T+900..1500  SEGUNDA PARED: peor que la primera — viento 1.5x,
 *                metralla, pops de totem
 * Los proyectiles e items derivan con el viento.
 */
public class HurricaneSession extends DisasterSession {

    private static final int EYE_START = 600;
    private static final int EYE_END = 900;
    private static final int DURATION = 1500;

    private int shredCooldown;

    public HurricaneSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
        createBossBar(Component.translatable(kind().bossbarKey()),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_12, false);
    }

    @Override
    public Disasters kind() {
        return Disasters.HURACAN;
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
            bossBar.setProgress((float) age / DURATION);
        }

        boolean inEye = age >= EYE_START && age < EYE_END;

        if (inEye) {
            // EL OJO: calma total. Sin viento, sin lluvia, sin sonido.
            if (age == EYE_START) {
                FxDirector.sky(server, SkyFx.EYE_SEPIA, EYE_END - EYE_START, 1.0F);
                TitleDirector.title(target,
                        Component.translatable("cataclysm.huracan.eye.title"),
                        Component.translatable("cataclysm.huracan.eye.subtitle"));
            }
            if (age % 60 == 0) {
                TitleDirector.actionbar(target, Component.translatable("cataclysm.huracan.eye.calm"));
            }
            return;
        }

        boolean secondWall = age >= EYE_END;
        float severity = secondWall ? 1.5F : Math.min(1.0F, age / 300.0F);
        if (age == EYE_END) {
            TitleDirector.title(target,
                    Component.translatable("cataclysm.huracan.wall2.title"),
                    Component.translatable("cataclysm.huracan.wall2.subtitle"));
        }

        // viento ciclonico sostenido: direccion que rota lentamente + rafagas
        double windAngle = age * 0.012D + spreadIndex;
        double gust = 1.0D + 0.5D * Math.sin(age * 0.11D) + random.nextDouble() * 0.3D;
        Vec3 force = new Vec3(Math.cos(windAngle), 0.0D, Math.sin(windAngle))
                .scale(0.045D * severity * gust);
        Physics.wind(target, force);

        // items y proyectiles derivan con el viento
        AABB around = target.getBoundingBox().inflate(24.0D, 12.0D, 24.0D);
        for (Entity drifting : level.getEntitiesOfClass(Entity.class, around,
                e -> e instanceof ItemEntity || e instanceof Projectile)) {
            Physics.wind(drifting, force.scale(1.6D));
        }

        // bandas de lluvia alrededor
        if (age % 6 == 0) {
            double bandAngle = random.nextDouble() * Math.PI * 2.0D;
            double bandDist = 8.0D + random.nextDouble() * 14.0D;
            Vec3 cell = target.position().add(
                    Math.cos(bandAngle) * bandDist, 12.0D, Math.sin(bandAngle) * bandDist);
            FxDirector.fire(level, FxEvent.RAIN_CELL, cell, severity, 10);
        }
        if (age % 45 == 0) {
            FxDirector.fire(level, FxEvent.WIND_GUST, target.position(), severity,
                    (int) Math.toDegrees(windAngle));
            FxDirector.sound(level, target.position(), ModSounds.WIND_GUST.get(),
                    secondWall ? 3.0F : 2.0F, 0.8F + random.nextFloat() * 0.3F);
        }

        // segunda pared: metralla y pops
        if (secondWall) {
            if (age % 10 == 0) {
                target.hurt(ModDamageTypes.source(level, ModDamageTypes.DEBRIS), 2.0F);
            }
            if (--shredCooldown <= 0) {
                shredCooldown = 120;
                TotemShredder.start(target, kind().commandName(), 2,
                        CataclysmConfig.COMMON.shredIntervalTicks.get());
            }
        }
    }

    @Override
    protected void onEnd(String reason) {
        FxDirector.skyClear(server, SkyFx.EYE_SEPIA);
    }
}
