package com.mojang.blaze3d.vertex;

public class BufferBuilder implements VertexConsumer {
    public static class RenderedBuffer {}
    public void begin(VertexFormat.Mode mode, VertexFormat format) {}
    public RenderedBuffer end() { throw new UnsupportedOperationException(); }
    @Override public VertexConsumer vertex(org.joml.Matrix4f matrix, float x, float y, float z) { return this; }
    @Override public VertexConsumer color(float r, float g, float b, float a) { return this; }
    @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
    @Override public void endVertex() {}
}
