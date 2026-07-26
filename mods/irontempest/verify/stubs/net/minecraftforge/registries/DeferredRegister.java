package net.minecraftforge.registries;

import java.util.function.Supplier;
import net.minecraftforge.eventbus.api.IEventBus;

public class DeferredRegister<T> {
    public static <B> DeferredRegister<B> create(IForgeRegistry<B> registry, String modid) {
        throw new UnsupportedOperationException();
    }

    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> supplier) {
        throw new UnsupportedOperationException();
    }

    public void register(IEventBus bus) {}
}
