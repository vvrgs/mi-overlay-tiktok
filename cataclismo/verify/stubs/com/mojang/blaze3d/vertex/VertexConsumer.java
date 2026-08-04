package com.mojang.blaze3d.vertex;

public interface VertexConsumer {
    VertexConsumer vertex(org.joml.Matrix4f matrix, float x, float y, float z);
    VertexConsumer color(float r, float g, float b, float a);
    VertexConsumer color(int r, int g, int b, int a);
    void endVertex();
}
