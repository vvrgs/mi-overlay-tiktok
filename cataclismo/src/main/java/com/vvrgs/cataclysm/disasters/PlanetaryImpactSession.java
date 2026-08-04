package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.Anchors;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.Physics;
import com.vvrgs.cataclysm.core.TerrainBudget;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.core.TotemShredder;
import com.vvrgs.cataclysm.entity.BolideEntity;
import com.vvrgs.cataclysm.entity.ImpactorEntity;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.fx.SkyFx;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import com.vvrgs.cataclysm.registry.ModEntities;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /impacto — tier U (exclusion global). IMPACTO PLANETARIO, el final cosmico.
 *
 * Coreografia:
 *   T+0      aviso global: sirena + titulo a TODO el server, cielo rojo
 *   T+60     un punto de luz CRECE en el cielo (SkyFx: se ve venir)
 *   T+160    entrada atmosferica: el impactor REAL (entidad a escala) cruza
 *            el cielo con estela, sonido rasgado
 *   T+~200   IMPACTO a 30-60 bl del jugador: flash blanco total, onda
 *            expansiva visible que avanza (anillo fisico), crater r=13
 *            amortizado, eyecta balistica incendiaria, hongo de polvo,
 *            oscurecimiento del cielo 90 s, trituradora a tope en la zona
 */
public class PlanetaryImpactSession extends DisasterSession {

    private static final int APPROACH_TICK = 60;
    private static final int ENTRY_TICK = 160;
    private static final int IMPACT_DEADLINE = 240;
    private static final int CRATER_RADIUS = 13;
    private static final double SHOCKWAVE_MAX = 60.0D;
    private static final double SHOCKWAVE_SPEED = 1.5D;
    private static final int AFTERMATH_TICKS = 1900;

    private ImpactorEntity impactor;
    private Vec3 impactPoint;
    private int impactTick = -1;
    private final Set<UUID> shocked = new HashSet<>();

    public PlanetaryImpactSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        chooseImpactPoint(target);
        createBossBar(Component.translatable(kind().bossbarKey()),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10, true);
    }

    @Override
    public Disasters kind() {
        return Disasters.IMPACTO;
    }

    @Override
    protected int maxLifetimeTicks() {
        return IMPACT_DEADLINE + AFTERMATH_TICKS + 100;
    }

    private void chooseImpactPoint(ServerPlayer target) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double dist = 30.0D + random.nextDouble() * 30.0D;
        double x = target.getX() + Math.cos(angle) * dist;
        double z = target.getZ() + Math.sin(angle) * dist;
        double y = Anchors.surfaceY(target.serverLevel(), Mth.floor(x), Mth.floor(z));
        impactPoint = new Vec3(x, y, z);
    }

    @Override
    protected void onTick(ServerPlayer target) {
        // ==== T+0: aviso global ====
        if (age == 1) {
            if (CataclysmConfig.COMMON.broadcastMessages.get()) {
                FxDirector.globalSound(server, ModSounds.SIREN.get(), 1.0F, 1.0F);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    TitleDirector.title(player,
                            Component.translatable("cataclysm.impacto.global.title"),
                            Component.translatable("cataclysm.impacto.global.subtitle"));
                }
            }
            FxDirector.sky(server, SkyFx.RED_TINT, ENTRY_TICK, 0.6F);
        }

        // ==== T+60: el punto de luz crece ====
        if (age == APPROACH_TICK) {
            FxDirector.sky(server, SkyFx.IMPACT_APPROACH, ENTRY_TICK - APPROACH_TICK + 45, 1.0F);
        }

        // ==== T+160: entrada atmosferica ====
        if (age == ENTRY_TICK) {
            spawnImpactor(target);
            FxDirector.sky(server, SkyFx.ATMOSPHERE_ENTRY, 60, 1.0F);
            FxDirector.globalSound(server, ModSounds.ATMOSPHERE_TEAR.get(), 0.8F, 0.9F);
        }

        // ==== impacto: cuando el impactor llega (failsafe en deadline) ====
        if (impactTick < 0) {
            boolean arrived = impactor != null && impactor.isAlive() && impactor.hasArrived();
            if (arrived || age >= IMPACT_DEADLINE) {
                doImpact(target);
            } else if (bossBar != null) {
                bossBar.setProgress(1.0F - (float) age / IMPACT_DEADLINE); // cuenta atras
                if (age % 20 == 0 && age > APPROACH_TICK) {
                    TitleDirector.actionbar(target, Component.translatable(
                            "cataclysm.impacto.countdown", (IMPACT_DEADLINE - age) / 20));
                }
            }
            return;
        }

        // ==== post-impacto ====
        int since = age - impactTick;
        if (bossBar != null) {
            bossBar.setProgress(Mth.clamp(1.0F - (float) since / AFTERMATH_TICKS, 0.0F, 1.0F));
        }

        // onda expansiva fisica que avanza como anillo
        double ring = since * SHOCKWAVE_SPEED;
        if (ring <= SHOCKWAVE_MAX) {
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                    impactor != null ? impactor.getBoundingBox().inflate(SHOCKWAVE_MAX + 10.0D)
                            : target.getBoundingBox().inflate(SHOCKWAVE_MAX + 10.0D))) {
                double d = Math.sqrt(Math.pow(living.getX() - impactPoint.x, 2)
                        + Math.pow(living.getZ() - impactPoint.z, 2));
                if (Math.abs(d - ring) < 2.5D && shocked.add(living.getUUID())) {
                    living.invulnerableTime = 0;
                    living.hurt(ModDamageTypes.source(level, ModDamageTypes.SHOCKWAVE), 12.0F);
                    Physics.blast(living, impactPoint, SHOCKWAVE_MAX, 2.2D, 0.8D);
                }
            }
        }

        if (since % 120 == 0 && since < 600) {
            FxDirector.sound(level, impactPoint, ModSounds.RUMBLE.get(), 3.0F, 0.6F);
        }
        if (since % 15 == 0 && since < 1200) {
            FxDirector.fire(level, FxEvent.ASH_FALL, target.position(), 0.8F, 24);
        }
        if (since >= AFTERMATH_TICKS) {
            end("finished");
        }
    }

    private void spawnImpactor(ServerPlayer target) {
        double entryAngle = random.nextDouble() * Math.PI * 2.0D;
        Vec3 spawn = impactPoint.add(Math.cos(entryAngle) * 90.0D, 150.0D, Math.sin(entryAngle) * 90.0D);
        int flight = 40;
        Vec3 velocity = impactPoint.subtract(spawn).scale(1.0D / flight);

        ImpactorEntity entity = new ImpactorEntity(ModEntities.IMPACTOR.get(), level);
        entity.setPos(spawn.x, spawn.y, spawn.z);
        entity.configure(velocity, 5.0F, impactPoint.y + 2.0D);
        level.addFreshEntity(entity);
        this.impactor = entity;
    }

    private void doImpact(ServerPlayer target) {
        impactTick = age;
        if (impactor != null) {
            impactor.discard();
        }
        // flash blanco total + anillo + hongo
        FxDirector.fire(level, FxEvent.IMPACT_FLASH, impactPoint, 2.0F);
        FxDirector.fire(level, FxEvent.SHOCKWAVE_RING, impactPoint, 1.5F, (int) SHOCKWAVE_MAX);
        FxDirector.fire(level, FxEvent.IMPACT_MUSHROOM, impactPoint, 1.5F);
        FxDirector.sound(level, impactPoint, ModSounds.IMPACT_BLAST.get(), 4.0F, 0.55F);
        FxDirector.globalSound(server, ModSounds.IMPACT_BLAST.get(), 0.9F, 0.5F);
        FxDirector.sky(server, SkyFx.ASH_DARKEN, AFTERMATH_TICKS, 0.85F);
        FxDirector.skyClear(server, SkyFx.IMPACT_APPROACH);

        // crater r=13 amortizado (esfera achatada, del centro hacia fuera)
        BlockPos center = BlockPos.containing(impactPoint);
        List<BlockPos> craterOps = new ArrayList<>();
        int r = Math.min(CRATER_RADIUS, CataclysmConfig.COMMON.maxCraterRadius.get());
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-r, -r / 2, -r), center.offset(r, r / 3, r))) {
            double dx = (pos.getX() - center.getX()) / (double) r;
            double dy = (pos.getY() - center.getY()) / (r * 0.55D);
            double dz = (pos.getZ() - center.getZ()) / (double) r;
            if (dx * dx + dy * dy + dz * dz <= 1.0D) {
                craterOps.add(pos.immutable());
            }
        }
        craterOps.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
        for (BlockPos pos : craterOps) {
            TerrainBudget.carve(level, pos);
        }

        // eyecta balistica incendiaria con telegraphs
        for (int i = 0; i < 14; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double d = 15.0D + random.nextDouble() * 35.0D;
            double ex = impactPoint.x + Math.cos(a) * d;
            double ez = impactPoint.z + Math.sin(a) * d;
            double ey = Anchors.surfaceY(level, Mth.floor(ex), Mth.floor(ez));
            Vec3 landing = new Vec3(ex, ey, ez);
            FxDirector.fire(level, FxEvent.TELEGRAPH, landing, 0.8F, 20);
            Vec3 spawn = impactPoint.add(0, 6, 0);
            int flight = 22 + random.nextInt(12);
            BolideEntity ejecta = new BolideEntity(ModEntities.BOLIDE.get(), level);
            ejecta.setPos(spawn.x, spawn.y, spawn.z);
            Vec3 vel = new Vec3((landing.x - spawn.x) / flight,
                    (landing.y - spawn.y) / flight + 0.015D * flight / 2.0D,
                    (landing.z - spawn.z) / flight);
            ejecta.configure(vel, 0.6F + random.nextFloat() * 0.4F, 6.0F, 1, 0.8F);
            level.addFreshEntity(ejecta);
        }

        // trituradora a tope en la zona
        if (target.position().distanceTo(impactPoint) < 55.0D) {
            TotemShredder.start(target, kind().commandName(), 20,
                    Math.max(2, CataclysmConfig.COMMON.shredIntervalTicks.get() - 1));
        }
    }

    @Override
    protected void onReanchor(ServerPlayer target, boolean dimensionChange) {
        if (impactTick < 0) {
            // pre-impacto: el punto de impacto lo sigue a la nueva posicion
            chooseImpactPoint(target);
            if (impactor != null) {
                impactor.discard();
                impactor = null;
                if (age >= ENTRY_TICK) {
                    spawnImpactor(target); // discard + respawn, nunca teleportTo cross-dim
                }
            }
            shocked.clear();
        }
        // post-impacto: el crater es terreno, no se mueve
    }

    @Override
    protected void onEnd(String reason) {
        if (impactor != null) {
            impactor.discard();
            impactor = null;
        }
        FxDirector.skyClear(server, SkyFx.IMPACT_APPROACH);
        FxDirector.skyClear(server, SkyFx.ATMOSPHERE_ENTRY);
        FxDirector.skyClear(server, SkyFx.RED_TINT);
        if (reason.equals("stopall") || reason.equals("server_stopping")) {
            FxDirector.skyClear(server, SkyFx.ASH_DARKEN);
        }
    }
}
