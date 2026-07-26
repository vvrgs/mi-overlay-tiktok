package net.minecraftforge.event;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

public class TickEvent extends Event {
    public final Phase phase = Phase.START;

    public enum Phase {
        START,
        END
    }

    public static class ServerTickEvent extends TickEvent {
        public MinecraftServer getServer() { throw new UnsupportedOperationException(); }
    }

    public static class ClientTickEvent extends TickEvent {
    }
}
