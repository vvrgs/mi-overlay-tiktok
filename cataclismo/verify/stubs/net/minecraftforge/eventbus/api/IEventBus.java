package net.minecraftforge.eventbus.api;

public interface IEventBus {
    void register(Object target);
    <T extends Event> void addListener(java.util.function.Consumer<T> listener);
}
