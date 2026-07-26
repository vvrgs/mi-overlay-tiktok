package net.minecraft.core;

import net.minecraft.resources.ResourceKey;

public interface RegistryAccess {
    <E> Registry<E> registryOrThrow(ResourceKey<? extends Registry<? extends E>> key);
}
