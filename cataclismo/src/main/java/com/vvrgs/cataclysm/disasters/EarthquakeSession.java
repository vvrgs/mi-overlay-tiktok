package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.Anchors;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.Physics;
import com.vvrgs.cataclysm.core.TerrainBudget;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.core.TotemShredder;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * /terremoto — tier C. Terremoto que PARTE EL SUELO.
 *
 * Coreografia (45 s):
 *   T+0..100    TEMBLOR creciente: screen shake trauma2, polvo, retumbo
 *   T+100..~350 LA FISURA se abre BAJO LOS PIES del jugador: zanja de 3-5
 *               de ancho, 20-30 de profundidad, 44 de largo, excavada
 *               amortizada del centro hacia fuera, bordes desmoronandose
 *   T+100..500  SUCCION hacia la grieta; dentro: dano + trituradora
 *   T+500/600/700 REPLICAS decrecientes
 *   La fisura QUEDA en el mapa.
 */
public class EarthquakeSession extends DisasterSession {

    private static final int FISSURE_START = 100;
    private static final int SUCTION_END = 500;
    private static final int DURATION = 900;

    private record FissureColumn(int x, int z, int depth, int openTick) {
    }

    private final List<FissureColumn> plan = new ArrayList<>();
    private int planCursor;
    /** eje de la fisura (por donde pasa y hacia donde corre) */
    private double axisX;
    private double axisZ;
    private double axisAngle;
    /** altura del borde ORIGINAL de la fisura (pre-excavacion): comparar
     *  contra el heightmap vivo es una fase muerta — la propia zanja lo baja */
    private int rimY;
    private boolean swallowRewarded;
    /** true = el eje quedo en otra dimension y ya no se replanea: succion OFF */
    private boolean suctionDead;

    public EarthquakeSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
        createBossBar(Component.translatable(kind().bossbarKey()),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS, false);
        planFissure(target, FISSURE_START, 22);
    }

    @Override
    public Disasters kind() {
        return Disasters.TERREMOTO;
    }

    @Override
    protected int maxLifetimeTicks() {
        return DURATION + 60;
    }

    /** Precalcula las columnas de la zanja: se abre del centro hacia fuera. */
    private void planFissure(ServerPlayer target, int startTick, int halfLength) {
        plan.clear();
        planCursor = 0;
        axisX = target.getX();
        axisZ = target.getZ();
        axisAngle = random.nextDouble() * Math.PI * 2.0D;
        // superficie pre-excavacion (planFissure corre ANTES de carvear nada)
        rimY = Anchors.surfaceY(level, Mth.floor(axisX), Mth.floor(axisZ));
        double dirX = Math.sin(axisAngle);
        double dirZ = Math.cos(axisAngle);
        double perpX = dirZ;
        double perpZ = -dirX;
        int maxDepth = CataclysmConfig.COMMON.maxFissureDepth.get();

        for (int s = -halfLength; s <= halfLength; s++) {
            double t = Math.abs(s) / (double) halfLength;
            // ancho 3-5 con taper en las puntas; profundidad 20-30 con taper
            int halfWidth = Math.max(1, (int) Math.round(2.0D * (1.0D - t * t)));
            int depth = Math.max(6, (int) Math.round(
                    Math.min(maxDepth, 22 + random.nextInt(8)) * (1.0D - Math.pow(t, 1.5D))));
            int openTick = startTick + Math.abs(s) * 4 + random.nextInt(3);
            for (int w = -halfWidth; w <= halfWidth; w++) {
                int x = Mth.floor(axisX + dirX * s + perpX * w);
                int z = Mth.floor(axisZ + dirZ * s + perpZ * w);
                plan.add(new FissureColumn(x, z, depth, openTick));
            }
        }
        plan.sort((a, b) -> Integer.compare(a.openTick(), b.openTick()));
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

        // ==== fase temblor (siempre activa de fondo, decrece tras la fisura) ====
        float tremor;
        if (age < FISSURE_START) {
            tremor = age / (float) FISSURE_START; // crece 5 s
        } else if (age < SUCTION_END) {
            tremor = 1.0F - (age - FISSURE_START) / (float) (SUCTION_END - FISSURE_START) * 0.6F;
        } else {
            tremor = aftershock();
        }
        if (tremor > 0.05F && age % 5 == 0) {
            FxDirector.fire(level, FxEvent.QUAKE_DUST, target.position(), tremor);
        }
        if (age % 25 == 0 && tremor > 0.1F) {
            FxDirector.sound(level, target.position(), ModSounds.RUMBLE.get(),
                    2.0F + tremor * 1.5F, 0.7F + random.nextFloat() * 0.2F);
        }

        // ==== apertura de la fisura (amortizada, del centro hacia fuera) ====
        boolean opened = false;
        while (planCursor < plan.size() && plan.get(planCursor).openTick() <= age) {
            FissureColumn col = plan.get(planCursor++);
            carveColumn(col);
            opened = true;
        }
        if (opened && age % 4 == 0) {
            FxDirector.sound(level, target.position(), ModSounds.ROCK_CRACK.get(),
                    2.5F, 0.8F + random.nextFloat() * 0.4F);
        }

        // ==== succion hacia la grieta ====
        if (!suctionDead && age >= FISSURE_START && age < SUCTION_END) {
            Vec3 pos = target.position();
            // proyeccion del jugador sobre el eje de la fisura
            double dirX = Math.sin(axisAngle);
            double dirZ = Math.cos(axisAngle);
            double relX = pos.x - axisX;
            double relZ = pos.z - axisZ;
            double along = relX * dirX + relZ * dirZ;
            along = Mth.clamp(along, -22.0D, 22.0D);
            double nearX = axisX + dirX * along;
            double nearZ = axisZ + dirZ * along;
            double lateral = Math.sqrt((pos.x - nearX) * (pos.x - nearX)
                    + (pos.z - nearZ) * (pos.z - nearZ));

            if (lateral < 8.0D) {
                // ME TRAGA: tira hacia el eje y hacia abajo
                Physics.suction(target, new Vec3(nearX, pos.y - 2.0D, nearZ),
                        10.0D, 0.14D, 0.0D, -0.04D);
                if (age % 40 == 0) {
                    TitleDirector.actionbar(target,
                            Component.translatable("cataclysm.terremoto.warning"));
                }
            }
            // dentro de la grieta: dano + trituradora. Se compara contra el
            // borde ORIGINAL (rimY): el heightmap vivo ya bajo con la zanja
            if (lateral < 4.0D && pos.y < rimY - 3.0D) {
                if (age % 10 == 0) {
                    target.invulnerableTime = 0;
                    target.hurt(ModDamageTypes.source(level, ModDamageTypes.QUAKE), 3.0F);
                }
                if (!swallowRewarded) {
                    swallowRewarded = true;
                    TotemShredder.start(target, kind().commandName(), 8,
                            CataclysmConfig.COMMON.shredIntervalTicks.get());
                }
            }
        }
    }

    /** Replica decreciente en 500/600/700. */
    private float aftershock() {
        int[] shocks = {500, 600, 700};
        float[] strengths = {0.7F, 0.45F, 0.25F};
        for (int i = 0; i < shocks.length; i++) {
            int delta = age - shocks[i];
            if (delta >= 0 && delta < 60) {
                return strengths[i] * (1.0F - delta / 60.0F);
            }
        }
        return 0.0F;
    }

    private void carveColumn(FissureColumn col) {
        int surface = Anchors.surfaceY(level, col.x(), col.z());
        for (int dy = 0; dy < col.depth(); dy++) {
            TerrainBudget.carve(level, new BlockPos(col.x(), surface - dy, col.z()));
        }
        // FX de apertura a ras de suelo
        if (random.nextFloat() < 0.25F) {
            FxDirector.fire(level, FxEvent.FISSURE_BURST,
                    new Vec3(col.x() + 0.5D, surface, col.z() + 0.5D), 1.0F,
                    (int) Math.toDegrees(axisAngle));
        }
    }

    @Override
    protected void onReanchor(ServerPlayer target, boolean dimensionChange) {
        // el terremoto PERSIGUE: una nueva grieta (mas corta) se abre bajo
        // sus pies alla donde lo hayan teletransportado
        if (age < SUCTION_END - 60) {
            planFissure(target, age + 20, dimensionChange ? 14 : 18);
            swallowRewarded = false;
            suctionDead = false;
        } else if (dimensionChange) {
            // demasiado tarde para replanear: JAMAS arrastrar columnas ni
            // eje de succion de la dimension anterior a la nueva
            plan.clear();
            planCursor = 0;
            suctionDead = true;
        }
        // salto intra-dimension tardio: la fisura vieja termina de abrirse
        // en su sitio — "la fisura QUEDA en el mapa"
    }
}
