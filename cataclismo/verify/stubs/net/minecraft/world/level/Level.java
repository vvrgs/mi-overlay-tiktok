package net.minecraft.world.level;

public abstract class Level implements BlockGetter, LevelAccessor {
    public final net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
    public boolean isClientSide() { throw new UnsupportedOperationException(); }
    public net.minecraft.resources.ResourceKey<Level> dimension() { throw new UnsupportedOperationException(); }
    public net.minecraft.core.RegistryAccess registryAccess() { throw new UnsupportedOperationException(); }
    public long getGameTime() { throw new UnsupportedOperationException(); }
    public boolean isLoaded(net.minecraft.core.BlockPos pos) { throw new UnsupportedOperationException(); }
    public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) { throw new UnsupportedOperationException(); }
    public boolean setBlock(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int flags) { throw new UnsupportedOperationException(); }
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) { throw new UnsupportedOperationException(); }
    public int getMinBuildHeight() { throw new UnsupportedOperationException(); }
    public int getMaxBuildHeight() { throw new UnsupportedOperationException(); }
    public int getHeight(net.minecraft.world.level.levelgen.Heightmap.Types type, int x, int z) { throw new UnsupportedOperationException(); }
    public boolean addFreshEntity(net.minecraft.world.entity.Entity entity) { throw new UnsupportedOperationException(); }
    public <T extends net.minecraft.world.entity.Entity> java.util.List<T> getEntitiesOfClass(Class<T> clazz, net.minecraft.world.phys.AABB box) { throw new UnsupportedOperationException(); }
    public <T extends net.minecraft.world.entity.Entity> java.util.List<T> getEntitiesOfClass(Class<T> clazz, net.minecraft.world.phys.AABB box, java.util.function.Predicate<? super T> predicate) { throw new UnsupportedOperationException(); }
    public net.minecraft.world.phys.BlockHitResult clip(ClipContext context) { throw new UnsupportedOperationException(); }
    public void playSound(net.minecraft.world.entity.player.Player except, double x, double y, double z, net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source, float volume, float pitch) {}
    public void playLocalSound(double x, double y, double z, net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source, float volume, float pitch, boolean distanceDelay) {}
    public void addParticle(net.minecraft.core.particles.ParticleOptions options, double x, double y, double z, double dx, double dy, double dz) {}
}
