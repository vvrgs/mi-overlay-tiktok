package com.mojang.blaze3d.vertex;

public class BufferBuilder implements VertexConsumer {
    public BufferBuilder(int capacity) {}

    public void begin(VertexFormat.Mode mode, VertexFormat format) { throw new UnsupportedOperationException(); }
    public RenderedBuffer end() { throw new UnsupportedOperationException(); }

    @Override
    public VertexConsumer vertex(double x, double y, double z) { throw new UnsupportedOperationException(); }
    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) { throw new UnsupportedOperationException(); }
    @Override
    public VertexConsumer uv(float u, float v) { throw new UnsupportedOperationException(); }
    @Override
    public VertexConsumer uv2(int u, int v) { throw new UnsupportedOperationException(); }
    @Override
    public void endVertex() { throw new UnsupportedOperationException(); }

    public static class RenderedBuffer {
    }
}
