package net.minecraft.resources;

import net.minecraft.core.Registry;

public class ResourceKey<T> {
    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registryKey, ResourceLocation location) {
        throw new UnsupportedOperationException();
    }
}
