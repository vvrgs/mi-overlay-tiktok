package net.minecraftforge.network.simple;

public class SimpleChannel {
    public static class MessageBuilder<MSG> {
        public MessageBuilder<MSG> encoder(java.util.function.BiConsumer<MSG, net.minecraft.network.FriendlyByteBuf> encoder) { return this; }
        public MessageBuilder<MSG> decoder(java.util.function.Function<net.minecraft.network.FriendlyByteBuf, MSG> decoder) { return this; }
        public MessageBuilder<MSG> consumerMainThread(java.util.function.BiConsumer<MSG, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context>> consumer) { return this; }
        public void add() {}
    }
    public <MSG> MessageBuilder<MSG> messageBuilder(Class<MSG> type, int id, net.minecraftforge.network.NetworkDirection direction) { throw new UnsupportedOperationException(); }
    public <MSG> void send(net.minecraftforge.network.PacketDistributor.PacketTarget target, MSG message) {}
}
