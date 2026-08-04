package net.minecraftforge.registries;

public class DeferredRegister<T> {
    public static <B> DeferredRegister<B> create(IForgeRegistry<B> registry, String modid) { throw new UnsupportedOperationException(); }
    public <I extends T> RegistryObject<I> register(String name, java.util.function.Supplier<? extends I> supplier) { throw new UnsupportedOperationException(); }
    public void register(net.minecraftforge.eventbus.api.IEventBus bus) {}
}
