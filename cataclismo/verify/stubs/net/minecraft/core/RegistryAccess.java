package net.minecraft.core;

public interface RegistryAccess {
    <T> Registry<T> registryOrThrow(net.minecraft.resources.ResourceKey<? extends Registry<? extends T>> key);
}
