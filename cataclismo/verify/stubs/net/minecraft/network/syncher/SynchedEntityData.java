package net.minecraft.network.syncher;

public class SynchedEntityData {
    public static <T> EntityDataAccessor<T> defineId(Class<? extends net.minecraft.world.entity.Entity> clazz, EntityDataSerializer<T> serializer) { throw new UnsupportedOperationException(); }
    public <T> void define(EntityDataAccessor<T> accessor, T defaultValue) {}
    public <T> T get(EntityDataAccessor<T> accessor) { throw new UnsupportedOperationException(); }
    public <T> void set(EntityDataAccessor<T> accessor, T value) {}
}
