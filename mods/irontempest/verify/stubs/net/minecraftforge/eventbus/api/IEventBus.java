package net.minecraftforge.eventbus.api;

import java.util.function.Consumer;

public interface IEventBus {
    void register(Object target);
    <T extends Event> void addListener(Consumer<T> consumer);
}
