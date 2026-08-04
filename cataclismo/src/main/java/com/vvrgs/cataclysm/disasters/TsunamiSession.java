package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.Anchors;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.Physics;
import com.vvrgs.cataclysm.core.TerrainBudget;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.core.TotemShredder;
import com.vvrgs.cataclysm.entity.TsunamiWallEntity;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModEntities;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * /tsunami — tier C. Muro de agua de 11 de alto y ~45 de frente.
 *
 * Coreografia (~55 s):
 *   T+0..100    SE VE VENIR: rugido lejano, titulo, el muro nace a 48 bl
 *   T+100..~350 AVANCE: 6 segmentos arrasan (impulso masivo + dano), agua
 *               real TEMPORAL colocada amortizadamente tras la cresta
 *   despues     RESACA: 5 s de arrastre de vuelta hacia el mar + escombros
 *   final       LIMPIEZA: toda el agua temporal se retira amortizadamente
 */
public class TsunamiSession extends DisasterSession {

    private static final int WALL_SPAWN = 20;
    private static final int SEGMENTS = 6;
    private static final double START_DISTANCE = 48.0D;
    private static final int RESACA_TICKS = 100;
    private static final int DURATION = 1150;
    private static final int MAX_WATER_RECORDS = 6000;

    private final List<TsunamiWallEntity> walls = new ArrayList<>();
    private final List<Long> placedWater = new ArrayList<>();
    private ServerLevel waterLevel;
    private double headingRad;
    private int resacaLeft = -1;
    private boolean cleanupQueued;
    private boolean shredRewarded;

    public TsunamiSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
        createBossBar(Component.translatable(kind().bossbarKey()),
                BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS, false);
    }

    @Override
    public Disasters kind() {
        return Disasters.TSUNAMI;
    }

    @Override
    protected int maxLifetimeTicks() {
        return DURATION + 100;
    }

    @Override
    protected void onTick(ServerPlayer target) {
        if (age >= DURATION) {
            queueCleanup();
            end("finished");
            return;
        }
        if (bossBar != null) {
            // progreso REAL: distancia recorrida por el frente
            float progress = walls.isEmpty()
                    ? (resacaLeft >= 0 ? 1.0F : (float) age / 100.0F)
                    : 1.0F - (float) Math.min(1.0, nearestWallDistance(target) / START_DISTANCE);
            bossBar.setProgress(Mth.clamp(progress, 0.0F, 1.0F));
        }

        if (age == 1) {
            FxDirector.sound(level, target.position(), ModSounds.TSUNAMI_ROAR.get(), 4.0F, 0.8F);
        }
        if (age == WALL_SPAWN) {
            spawnFront(target, START_DISTANCE);
        }

        walls.removeIf(wall -> wall.isRemoved());

        if (!walls.isEmpty()) {
            // rugido persistente del muro acercandose
            if (age % 40 == 0) {
                FxDirector.sound(level, walls.get(0).position(),
                        ModSounds.TSUNAMI_ROAR.get(), 3.5F, 0.9F);
            }
            // agua real temporal tras la cresta (amortizada y registrada)
            if (age % 8 == 0 && waterLevel != null) {
                for (TsunamiWallEntity wall : walls) {
                    placeWaterBehind(wall);
                }
            }
            // pops cuando la ola lo engulle
            if (!shredRewarded) {
                for (TsunamiWallEntity wall : walls) {
                    if (wall.position().distanceTo(target.position()) < 4.0D) {
                        shredRewarded = true;
                        TotemShredder.start(target, kind().commandName(), 4,
                                CataclysmConfig.COMMON.shredIntervalTicks.get());
                        break;
                    }
                }
            }
            // el frente paso de largo 20 bl => empieza la resaca
            boolean allPast = true;
            for (TsunamiWallEntity wall : walls) {
                Vec3 toPlayer = target.position().subtract(wall.position());
                double along = toPlayer.x * Math.sin(headingRad) + toPlayer.z * Math.cos(headingRad);
                if (along > -20.0D) {
                    allPast = false;
                    break;
                }
            }
            if (allPast) {
                for (TsunamiWallEntity wall : walls) {
                    wall.discard();
                }
                walls.clear();
            }
        } else if (age > WALL_SPAWN + 40 && resacaLeft < 0) {
            resacaLeft = RESACA_TICKS;
            TitleDirector.actionbar(target, Component.translatable("cataclysm.tsunami.resaca"));
        }

        // ==== RESACA: arrastre de vuelta hacia el mar ====
        if (resacaLeft > 0) {
            resacaLeft--;
            Vec3 seaward = new Vec3(-Math.sin(headingRad), 0.0D, -Math.cos(headingRad))
                    .scale(0.09D * (resacaLeft / (double) RESACA_TICKS));
            Physics.wind(target, seaward);
            if (age % 10 == 0) {
                FxDirector.fire(level, FxEvent.TSUNAMI_SPRAY, target.position(), 0.6F);
            }
            if (age % 30 == 0) {
                FxDirector.fire(level, FxEvent.TORNADO_DEBRIS, target.position(), 0.5F, 0);
            }
        } else if (resacaLeft == 0) {
            resacaLeft = -1;
            queueCleanup();
            end("finished");
        }
    }

    private double nearestWallDistance(ServerPlayer target) {
        double best = START_DISTANCE;
        for (TsunamiWallEntity wall : walls) {
            best = Math.min(best, wall.position().distanceTo(target.position()));
        }
        return best;
    }

    private void spawnFront(ServerPlayer target, double distance) {
        headingRad = random.nextDouble() * Math.PI * 2.0D;
        waterLevel = level;
        // el muro avanza HACIA el jugador: nace a barlovento
        double originX = target.getX() - Math.sin(headingRad) * distance;
        double originZ = target.getZ() - Math.cos(headingRad) * distance;
        double perpX = Math.cos(headingRad);
        double perpZ = -Math.sin(headingRad);
        for (int i = 0; i < SEGMENTS; i++) {
            double off = (i - (SEGMENTS - 1) / 2.0D) * 7.5D;
            double x = originX + perpX * off;
            double z = originZ + perpZ * off;
            double y = Anchors.surfaceY(level, Mth.floor(x), Mth.floor(z));
            TsunamiWallEntity wall = new TsunamiWallEntity(ModEntities.TSUNAMI_WALL.get(), level);
            wall.setPos(x, y, z);
            wall.setHeading(headingRad);
            wall.setLifeTicks(20 * 40);
            level.addFreshEntity(wall);
            walls.add(wall);
        }
    }

    /** Franja de agua fuente 3x2 tras la cresta, registrada para la limpieza. */
    private void placeWaterBehind(TsunamiWallEntity wall) {
        if (placedWater.size() >= MAX_WATER_RECORDS) {
            return;
        }
        double backX = wall.getX() - Math.sin(headingRad) * 3.0D;
        double backZ = wall.getZ() - Math.cos(headingRad) * 3.0D;
        double perpX = Math.cos(headingRad);
        double perpZ = -Math.sin(headingRad);
        for (int w = -1; w <= 1; w++) {
            int x = Mth.floor(backX + perpX * w * 2.5D);
            int z = Mth.floor(backZ + perpZ * w * 2.5D);
            int y = Anchors.surfaceY(waterLevel, x, z);
            for (int dy = 0; dy < 2; dy++) {
                BlockPos pos = new BlockPos(x, y + dy, z);
                if (waterLevel.isLoaded(pos) && waterLevel.getBlockState(pos).isAir()) {
                    TerrainBudget.placeFluid(waterLevel, pos, Blocks.WATER.defaultBlockState());
                    placedWater.add(pos.asLong());
                }
            }
        }
    }

    /** Toda el agua temporal se retira amortizadamente (aunque el toggle de
     *  terreno este apagado: es limpieza propia). */
    private void queueCleanup() {
        if (cleanupQueued || waterLevel == null) {
            return;
        }
        cleanupQueued = true;
        for (Long packed : placedWater) {
            TerrainBudget.restore(waterLevel, BlockPos.of(packed), Blocks.AIR.defaultBlockState());
        }
        placedWater.clear();
    }

    @Override
    protected void onReanchor(ServerPlayer target, boolean dimensionChange) {
        // discard + respawn del frente a 30 bl del nuevo anclaje
        for (TsunamiWallEntity wall : walls) {
            wall.discard();
        }
        walls.clear();
        // el agua vieja queda registrada y se limpiara igual (ops guardan su level)
        if (age < DURATION - 300) {
            spawnFront(target, 30.0D);
        }
    }

    @Override
    protected void onEnd(String reason) {
        for (TsunamiWallEntity wall : walls) {
            wall.discard();
        }
        walls.clear();
        queueCleanup();
    }
}
