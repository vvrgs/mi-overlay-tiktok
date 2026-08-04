package net.minecraft.client.renderer;

public interface MultiBufferSource {
    com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType type);
}
