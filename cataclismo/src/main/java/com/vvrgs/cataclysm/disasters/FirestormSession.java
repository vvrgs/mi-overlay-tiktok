package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.core.Afflictions;
import com.vvrgs.cataclysm.core.Anchors;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.TerrainBudget;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * /fuego — tier M. Tormenta de fuego.
 *
 * Un FRENTE de llamas de 20 de ancho avanza hacia el jugador desde la
 * direccion del abanico: brasas ascendentes a contraluz, ignicion del
 * terreno en parches, columnas de humo, y DoT napalm que ignora armadura
 * (y PERSIGUE: sigue quemando aunque huya al End).
 */
public class FirestormSession extends DisasterSession {

    private static final int DURATION = 20 * 45;
    private static final double FRONT_SPEED = 0.28D;
    private static final double FRONT_HALF_WIDTH = 10.0D;
    private static final double START_DISTANCE = 26.0D;

    /** posicion del centro del frente y rumbo de avance */
    private double frontX;
    private double frontZ;
    private double headingRad;

    public FirestormSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        // nace a barlovento del abanico: cada sesion M viene de un rumbo distinto
        this.headingRad = (spreadIndex * (Math.PI * 2.0D / 5.0D)) + random.nextDouble() * 0.5D;
        this.frontX = target.getX() - Math.sin(headingRad) * START_DISTANCE;
        this.frontZ = target.getZ() - Math.cos(headingRad) * START_DISTANCE;
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
    }

    @Override
    public Disasters kind() {
        return Disasters.FUEGO;
    }

    @Override
    protected int maxLifetimeTicks() {
        return DURATION + 40;
    }

    @Override
    protected void onReanchor(ServerPlayer target, boolean dimensionChange) {
        // el frente se recoloca a barlovento de la nueva posicion
        this.frontX = target.getX() - Math.sin(headingRad) * START_DISTANCE * 0.7D;
        this.frontZ = target.getZ() - Math.cos(headingRad) * START_DISTANCE * 0.7D;
    }

    @Override
    protected void onTick(ServerPlayer target) {
        if (age >= DURATION) {
            end("finished");
            return;
        }
        ServerLevel level = this.level;

        // el frente CORRIGE su rumbo hacia el jugador (persigue, no pasa de largo)
        Vec3 toTarget = new Vec3(target.getX() - frontX, 0.0D, target.getZ() - frontZ);
        double dist = toTarget.length();
        if (dist > 1.0D) {
            double desired = Mth.atan2(toTarget.x, toTarget.z);
            double delta = Mth.wrapDegrees(Math.toDegrees(desired - headingRad));
            headingRad += Math.toRadians(Mth.clamp(delta, -2.0D, 2.0D));
            frontX += Math.sin(headingRad) * FRONT_SPEED;
            frontZ += Math.cos(headingRad) * FRONT_SPEED;
        }

        // burst de llamas + brasas a lo largo del frente (5 puntos)
        if (age % 5 == 0) {
            double perpX = Math.cos(headingRad);
            double perpZ = -Math.sin(headingRad);
            for (int i = -2; i <= 2; i++) {
                double px = frontX + perpX * i * (FRONT_HALF_WIDTH / 2.0D);
                double pz = frontZ + perpZ * i * (FRONT_HALF_WIDTH / 2.0D);
                double py = Anchors.surfaceY(level, Mth.floor(px), Mth.floor(pz));
                FxDirector.fire(level, FxEvent.FIRE_BURST, new Vec3(px, py, pz), 1.0F);
            }
        }
        if (age % 15 == 0) {
            FxDirector.fire(level, FxEvent.EMBER_FIELD, target.position(), 0.8F);
        }
        if (age % 40 == 0) {
            double bx = frontX + (random.nextDouble() - 0.5D) * FRONT_HALF_WIDTH * 2.0D;
            double bz = frontZ + (random.nextDouble() - 0.5D) * FRONT_HALF_WIDTH * 2.0D;
            double by = Anchors.surfaceY(level, Mth.floor(bx), Mth.floor(bz));
            FxDirector.fire(level, FxEvent.SMOKE_COLUMN, new Vec3(bx, by, bz), 1.0F);
            FxDirector.sound(level, new Vec3(bx, by, bz), ModSounds.WIND_GUST.get(), 1.4F, 0.7F);
        }

        // ignicion del terreno en parches (amortizada)
        if (age % 4 == 0) {
            double perpX = Math.cos(headingRad);
            double perpZ = -Math.sin(headingRad);
            double off = (random.nextDouble() - 0.5D) * FRONT_HALF_WIDTH * 2.0D;
            int fx = Mth.floor(frontX + perpX * off);
            int fz = Mth.floor(frontZ + perpZ * off);
            int fy = Anchors.surfaceY(level, fx, fz);
            BlockPos firePos = new BlockPos(fx, fy, fz);
            if (level.isLoaded(firePos) && level.getBlockState(firePos).isAir()
                    && level.getBlockState(firePos.below()).isSolidRender(level, firePos.below())) {
                TerrainBudget.place(level, firePos, Blocks.FIRE.defaultBlockState());
            }
        }

        // el frente alcanza al jugador: napalm que ignora armadura y persigue
        if (dist < FRONT_HALF_WIDTH * 0.6D && age % 20 == 0) {
            Afflictions.apply(targetId, Afflictions.Kind.NAPALM, 2.5F, 20 * 8);
        }
    }
}
