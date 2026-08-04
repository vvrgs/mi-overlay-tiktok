package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.Physics;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.core.TotemShredder;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * /tormenta — tier M (hasta 5 solapadas por jugador, en abanico).
 *
 * 60 s de celula de tormenta SOBRE el jugador: nubes negras bajas (FX),
 * rayos encadenados cada 30-50 ticks que le BUSCAN (prediccion de
 * movimiento), viento lateral fisico con rafagas.
 */
public class ThunderstormSession extends DisasterSession {

    private static final int DURATION = 20 * 60;
    private static final int STRIKE_LEAD = 8;

    private int nextStrikeTick;
    private int dischargeTick = -1;
    private Vec3 predictedStrike = Vec3.ZERO;
    private int gustTicksLeft;
    private double gustAngle;

    public ThunderstormSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        this.nextStrikeTick = 30 + random.nextInt(20);
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
    }

    @Override
    public Disasters kind() {
        return Disasters.TORMENTA;
    }

    @Override
    protected int maxLifetimeTicks() {
        return DURATION + 40;
    }

    @Override
    protected void onTick(ServerPlayer target) {
        if (age >= DURATION) {
            end("finished");
            return;
        }
        ServerLevel level = target.serverLevel();

        // celula de nubes negras bajas sobre el jugador, desplazada en abanico
        // por indice para que 5 tormentas se lean como abanico, no amasijo
        double fanAngle = spreadIndex * (Math.PI * 2.0D / 5.0D);
        Vec3 cellCenter = target.position()
                .add(Math.cos(fanAngle) * spreadIndex * 6.0D, 14.0D, Math.sin(fanAngle) * spreadIndex * 6.0D);
        if (age % 8 == 0) {
            FxDirector.fire(level, FxEvent.RAIN_CELL, cellCenter, 1.0F, 14);
        }

        // viento lateral fisico con rafagas
        if (gustTicksLeft > 0) {
            gustTicksLeft--;
            double envelope = Math.sin(Math.PI * gustTicksLeft / 30.0D);
            Vec3 force = new Vec3(Math.cos(gustAngle), 0.0D, Math.sin(gustAngle))
                    .scale(0.06D * envelope);
            Physics.wind(target, force);
        } else if (age % 50 == spreadIndex * 7 % 50 && random.nextFloat() < 0.6F) {
            gustTicksLeft = 30;
            gustAngle = random.nextDouble() * Math.PI * 2.0D;
            FxDirector.fire(level, FxEvent.WIND_GUST, target.position(), 0.8F,
                    (int) Math.toDegrees(gustAngle));
            FxDirector.sound(level, target.position(), ModSounds.WIND_GUST.get(), 1.6F,
                    0.9F + random.nextFloat() * 0.3F);
        }

        // rayo que BUSCA: predice a donde se mueve el jugador
        if (dischargeTick < 0 && age >= nextStrikeTick) {
            predictedStrike = target.position().add(target.getDeltaMovement().scale(STRIKE_LEAD));
            dischargeTick = age + STRIKE_LEAD;
            FxDirector.fire(level, FxEvent.IONIZATION, predictedStrike, 0.8F);
            FxDirector.sound(level, predictedStrike, ModSounds.IONIZE.get(), 1.5F, 1.1F);
        }
        if (dischargeTick >= 0 && age >= dischargeTick) {
            dischargeTick = -1;
            nextStrikeTick = age + 30 + random.nextInt(21);
            FxDirector.fire(level, FxEvent.LIGHTNING_BOLT, predictedStrike, 1.0F, 30);
            FxDirector.sound(level, predictedStrike, ModSounds.THUNDER.get(), 3.0F,
                    0.85F + random.nextFloat() * 0.3F);
            // la prediccion acerto si el jugador esta cerca del punto
            if (target.position().distanceTo(predictedStrike) < 3.5D) {
                target.invulnerableTime = 0;
                target.hurt(ModDamageTypes.source(level, ModDamageTypes.LIGHTNING), 6.0F);
                TotemShredder.start(target, kind().commandName(), 1,
                        CataclysmConfig.COMMON.shredIntervalTicks.get());
            }
        }
    }
}
