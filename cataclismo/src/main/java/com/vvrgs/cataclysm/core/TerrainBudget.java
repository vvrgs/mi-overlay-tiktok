package com.vvrgs.cataclysm.core;

import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.CataclysmConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;

/**
 * Presupuesto GLOBAL de terreno: toda excavacion/colocacion (crateres,
 * fisura, cono del volcan, agua del tsunami) pasa por aqui. Un tick JAMAS
 * se congela por terreno.
 *
 * Guards en el momento de aplicar (no de encolar — el mundo cambia):
 * nunca bedrock ni bloques indestructibles, nunca blockentities con
 * inventario, fluidos fuente solo si el efecto lo pide.
 */
public final class TerrainBudget {

    private record BlockOp(ServerLevel level, BlockPos pos, BlockState state,
                           boolean allowFluid, boolean force) {
    }

    private static final ArrayDeque<BlockOp> QUEUE = new ArrayDeque<>();
    private static long dropped;

    /** Encola una operacion de bloque. force=true solo para limpieza propia
     *  (retirar el agua temporal del tsunami) — ignora el toggle de config. */
    public static void push(ServerLevel level, BlockPos pos, BlockState state,
                            boolean allowFluid, boolean force) {
        if (!force && !CataclysmConfig.COMMON.terrainDestruction.get()) {
            return;
        }
        if (QUEUE.size() >= CataclysmConfig.COMMON.maxQueuedBlockOps.get()) {
            if (++dropped % 10000 == 1) {
                Cataclysm.LOGGER.warn("[cataclysm] cola de terreno llena: {} ops descartadas", dropped);
            }
            return;
        }
        QUEUE.addLast(new BlockOp(level, pos.immutable(), state, allowFluid, force));
    }

    /** Excava (aire), sin drops. */
    public static void carve(ServerLevel level, BlockPos pos) {
        push(level, pos, Blocks.AIR.defaultBlockState(), false, false);
    }

    /** Coloca un bloque solido. */
    public static void place(ServerLevel level, BlockPos pos, BlockState state) {
        push(level, pos, state, false, false);
    }

    /** Coloca un fluido fuente (agua del tsunami, lava del volcan). */
    public static void placeFluid(ServerLevel level, BlockPos pos, BlockState state) {
        push(level, pos, state, true, false);
    }

    /** Limpieza propia: retirar fluidos/bloques temporales incluso con terrainDestruction=false. */
    public static void restore(ServerLevel level, BlockPos pos, BlockState state) {
        push(level, pos, state, true, true);
    }

    public static int queued() {
        return QUEUE.size();
    }

    public static void clear() {
        QUEUE.clear();
        dropped = 0;
    }

    static void drain() {
        if (QUEUE.isEmpty()) {
            return;
        }
        int budget = CataclysmConfig.COMMON.globalBlockBudgetPerTick.get();
        while (budget-- > 0) {
            BlockOp op = QUEUE.pollFirst();
            if (op == null) {
                return;
            }
            try {
                apply(op);
            } catch (Exception e) {
                Cataclysm.LOGGER.error("[cataclysm] op de terreno fallo en {} — descartada", op.pos(), e);
            }
        }
    }

    private static void apply(BlockOp op) {
        ServerLevel level = op.level();
        BlockPos pos = op.pos();
        if (level.isClientSide() || !level.isLoaded(pos)) {
            return;
        }
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (current.equals(op.state())) {
            return;
        }
        // nunca bedrock ni indestructibles (destroySpeed < 0)
        if (current.is(Blocks.BEDROCK) || current.getDestroySpeed(level, pos) < 0.0F) {
            return;
        }
        // nunca blockentities con inventario (cofres, hornos, shulkers)
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container) {
            return;
        }
        // nunca colocar fluidos fuente fuera del efecto que los pidio
        if (!op.allowFluid() && !op.state().getFluidState().isEmpty()) {
            return;
        }
        level.setBlock(pos, op.state(), 3);
    }

    private TerrainBudget() {
    }
}
