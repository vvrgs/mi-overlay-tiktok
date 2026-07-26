package net.minecraftforge.network.simple;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public class SimpleChannel {
    public <M> MessageBuilder<M> messageBuilder(Class<M> type, int id, NetworkDirection direction) {
        throw new UnsupportedOperationException();
    }

    public <MSG> void send(PacketDistributor.PacketTarget target, MSG message) {}

    public static class MessageBuilder<MSG> {
        public MessageBuilder<MSG> encoder(BiConsumer<MSG, FriendlyByteBuf> encoder) { return this; }
        public MessageBuilder<MSG> decoder(Function<FriendlyByteBuf, MSG> decoder) { return this; }
        public MessageBuilder<MSG> consumerMainThread(BiConsumer<MSG, Supplier<NetworkEvent.Context>> consumer) { return this; }
        public void add() {}
    }
}
