package net.minecraft.world.entity;

public class EntityType<T extends Entity> {
    @FunctionalInterface
    public interface EntityFactory<T extends Entity> {
        T create(EntityType<T> type, net.minecraft.world.level.Level level);
    }
    public static class Builder<T extends Entity> {
        public static <T extends Entity> Builder<T> of(EntityFactory<T> factory, MobCategory category) { throw new UnsupportedOperationException(); }
        public Builder<T> sized(float width, float height) { return this; }
        public Builder<T> clientTrackingRange(int chunks) { return this; }
        public Builder<T> updateInterval(int ticks) { return this; }
        public Builder<T> fireImmune() { return this; }
        public EntityType<T> build(String id) { throw new UnsupportedOperationException(); }
    }
}
