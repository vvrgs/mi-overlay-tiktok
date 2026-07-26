package com.mojang.blaze3d.vertex;

import org.joml.Matrix4f;

public interface VertexConsumer {
    VertexConsumer vertex(double x, double y, double z);
    VertexConsumer color(int red, int green, int blue, int alpha);
    VertexConsumer uv(float u, float v);
    VertexConsumer uv2(int u, int v);
    void endVertex();

    default VertexConsumer color(float red, float green, float blue, float alpha) { throw new UnsupportedOperationException(); }
    default VertexConsumer uv2(int lightmapUV) { throw new UnsupportedOperationException(); }
    default VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) { throw new UnsupportedOperationException(); }
}
