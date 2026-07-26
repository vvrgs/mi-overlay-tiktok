package net.minecraft.world.entity;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class EntityType<T extends Entity> {
    @Nullable
    public T create(Level level) { throw new UnsupportedOperationException(); }

    @FunctionalInterface
    public interface EntityFactory<T extends Entity> {
        T create(EntityType<T> type, Level level);
    }

    public static class Builder<T extends Entity> {
        public static <T extends Entity> Builder<T> of(EntityFactory<T> factory, MobCategory category) { throw new UnsupportedOperationException(); }
        public Builder<T> sized(float width, float height) { throw new UnsupportedOperationException(); }
        public Builder<T> clientTrackingRange(int range) { throw new UnsupportedOperationException(); }
        public Builder<T> updateInterval(int interval) { throw new UnsupportedOperationException(); }
        public Builder<T> fireImmune() { throw new UnsupportedOperationException(); }
        public Builder<T> noSummon() { throw new UnsupportedOperationException(); }
        public Builder<T> noSave() { throw new UnsupportedOperationException(); }
        public EntityType<T> build(String key) { throw new UnsupportedOperationException(); }
    }
}
