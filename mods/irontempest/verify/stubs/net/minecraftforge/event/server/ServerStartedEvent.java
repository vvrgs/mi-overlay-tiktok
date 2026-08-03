package net.minecraftforge.event.server;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

public class ServerStartedEvent extends Event {
    public MinecraftServer getServer() { throw new UnsupportedOperationException(); }
}
