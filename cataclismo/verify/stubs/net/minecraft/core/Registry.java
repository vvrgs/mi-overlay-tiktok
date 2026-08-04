package net.minecraft.core;

public interface Registry<T> {
    net.minecraft.core.Holder.Reference<T> getHolderOrThrow(net.minecraft.resources.ResourceKey<T> key);
}
