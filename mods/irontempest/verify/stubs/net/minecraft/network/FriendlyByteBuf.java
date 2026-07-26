package net.minecraft.network;

public class FriendlyByteBuf {
    public FriendlyByteBuf writeByte(int value) { throw new UnsupportedOperationException(); }
    public FriendlyByteBuf writeDouble(double value) { throw new UnsupportedOperationException(); }
    public FriendlyByteBuf writeFloat(float value) { throw new UnsupportedOperationException(); }
    public byte readByte() { return 0; }
    public double readDouble() { return 0.0D; }
    public float readFloat() { return 0.0F; }
}
