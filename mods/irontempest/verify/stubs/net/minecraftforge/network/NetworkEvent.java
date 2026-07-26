package net.minecraftforge.network;

import net.minecraftforge.eventbus.api.Event;

public class NetworkEvent extends Event {
    public static class Context {
        public void setPacketHandled(boolean handled) {}
    }
}
