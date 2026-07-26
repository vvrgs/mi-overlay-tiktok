package net.minecraft.core;

import net.minecraft.resources.ResourceKey;

public interface Registry<T> {
    Holder<T> getHolderOrThrow(ResourceKey<T> key);
}
