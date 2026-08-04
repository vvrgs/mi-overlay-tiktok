package net.minecraftforge.network;

public class NetworkEvent extends net.minecraftforge.eventbus.api.Event {
    public static class Context {
        public java.util.concurrent.CompletableFuture<Void> enqueueWork(Runnable work) { throw new UnsupportedOperationException(); }
        public void setPacketHandled(boolean handled) {}
    }
}
