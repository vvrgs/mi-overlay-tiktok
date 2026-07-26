package net.minecraftforge.registries;

import java.util.function.Supplier;

public final class RegistryObject<T> implements Supplier<T> {
    private RegistryObject() {}

    @Override
    public T get() { throw new UnsupportedOperationException(); }
}
