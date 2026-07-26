package net.minecraftforge.client.event;

import net.minecraftforge.eventbus.api.Event;

public abstract class ClientPlayerNetworkEvent extends Event {
    public static class LoggingOut extends ClientPlayerNetworkEvent {
    }
}
