package com.vvrgs.irontempest.server.util;

import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.config.WarConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Destrucción de terreno SIEMPRE amortizada. Regla dura del skill:
 * presupuesto global de bloques/tick + guardas (bedrock, fluidos, límites).
 */
public final class TerrainSculptor {

    private record Job(ServerLevel level, Iterator<BlockPos> positions, boolean fire) {}

    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static long blocksDestroyedTotal = 0L;

    /** Encola un cráter esférico amortizado centrado en {@code center}. */
    public static void crater(ServerLevel level, BlockPos center, int radius, boolean fire) {
        if (!WarConfig.TERRAIN_DESTRUCTION.get() || radius <= 0) {
            return;
        }
        List<BlockPos> positions = new ArrayList<>();
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        positions.add(center.offset(dx, dy, dz));
                    }
                }
            }
        }
        // De dentro hacia fuera: el cráter "crece" visiblemente.
        positions.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        JOBS.add(new Job(level, positions.iterator(), fire && WarConfig.LEAVE_FIRE.get()));
    }

    /** Quemadura cosmética del tier S: ≤5 bloques de superficie a fuego/aire. */
    public static void scorch(ServerLevel level, BlockPos center) {
        if (!WarConfig.TERRAIN_DESTRUCTION.get() || !WarConfig.COSMETIC_SCORCH.get()) {
            return;
        }
        List<BlockPos> positions = new ArrayList<>(5);
        positions.add(center);
        positions.add(center.north());
        positions.add(center.south());
        positions.add(center.east());
        positions.add(center.west());
        JOBS.add(new Job(level, positions.iterator(), WarConfig.LEAVE_FIRE.get()));
    }

    /** Llamar UNA vez por tick de servidor (desde SessionManager). */
    public static void serverTick() {
        int budget = WarConfig.GLOBAL_BLOCK_BUDGET_PER_TICK.get();
        while (budget > 0 && !JOBS.isEmpty()) {
            Job job = JOBS.peek();
            if (!job.positions().hasNext()) {
                JOBS.poll();
                continue;
            }
            while (budget > 0 && job.positions().hasNext()) {
                BlockPos pos = job.positions().next();
                if (destroyOne(job.level(), pos, job.fire())) {
                    budget--;
                }
            }
        }
    }

    private static boolean destroyOne(ServerLevel level, BlockPos pos, boolean fire) {
        if (pos.getY() <= level.getMinBuildHeight() + 1 || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        // Guardas: bedrock/irrompibles, fluidos (no drenar océanos), contenedores.
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.hasBlockEntity()) {
            return false;
        }
        boolean placed = false;
        if (fire && level.random.nextFloat() < 0.06F
                && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
            placed = level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
        } else {
            placed = level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        if (placed) {
            blocksDestroyedTotal++;
        }
        return placed;
    }

    /** Limpieza dura (parada de servidor): suelta todos los trabajos pendientes. */
    public static void clearAll() {
        int dropped = JOBS.size();
        JOBS.clear();
        if (dropped > 0) {
            IronTempest.LOGGER.info("[irontempest] TerrainSculptor: {} trabajos de terreno descartados (cleanup)", dropped);
        }
    }

    public static long totalDestroyed() {
        return blocksDestroyedTotal;
    }

    private TerrainSculptor() {}
}
