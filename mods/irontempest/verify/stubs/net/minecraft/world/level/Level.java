package net.minecraft.world.level;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public abstract class Level implements BlockGetter {
    public final boolean isClientSide = false;
    public final RandomSource random = null;

    protected Level() {}

    public boolean isClientSide() { return this.isClientSide; }
    public long getGameTime() { return 0L; }
    public BlockHitResult clip(ClipContext context) { throw new UnsupportedOperationException(); }
    public void addParticle(ParticleOptions options, double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed) {}
    public void playSound(@Nullable Player player, double x, double y, double z,
                          SoundEvent sound, SoundSource source, float volume, float pitch) {}
    public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource source,
                               float volume, float pitch, boolean distanceDelay) {}
    public RegistryAccess registryAccess() { throw new UnsupportedOperationException(); }
    public ResourceKey<Level> dimension() { throw new UnsupportedOperationException(); }
    @Nullable
    public MinecraftServer getServer() { return null; }
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> entityClass, AABB area) { throw new UnsupportedOperationException(); }
    public BlockState getBlockState(BlockPos pos) { throw new UnsupportedOperationException(); }
    public boolean setBlock(BlockPos pos, BlockState state, int flags) { return false; }
    public boolean isLoaded(BlockPos pos) { return false; }
    public int getMinBuildHeight() { return 0; }
    public int getMaxBuildHeight() { return 0; }
    public int getHeight(Heightmap.Types type, int x, int z) { return 0; }
    public boolean addFreshEntity(Entity entity) { return false; }
}
