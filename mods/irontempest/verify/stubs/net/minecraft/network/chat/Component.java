package net.minecraft.network.chat;

public interface Component {
    static MutableComponent translatable(String key) { throw new UnsupportedOperationException(); }
    static MutableComponent translatable(String key, Object... args) { throw new UnsupportedOperationException(); }
    static MutableComponent literal(String text) { throw new UnsupportedOperationException(); }
}
