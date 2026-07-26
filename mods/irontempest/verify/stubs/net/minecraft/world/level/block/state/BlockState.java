package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;

public class BlockState {
    public boolean isAir() { return false; }
    public float getDestroySpeed(BlockGetter level, BlockPos pos) { return 0.0F; }
    public FluidState getFluidState() { throw new UnsupportedOperationException(); }
    public boolean hasBlockEntity() { return false; }
    public boolean isSolidRender(BlockGetter level, BlockPos pos) { return false; }
}
