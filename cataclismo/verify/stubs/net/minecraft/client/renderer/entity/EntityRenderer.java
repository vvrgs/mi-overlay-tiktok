package net.minecraft.client.renderer.entity;

public abstract class EntityRenderer<T extends net.minecraft.world.entity.Entity> {
    protected EntityRenderer(EntityRendererProvider.Context context) {}
    public abstract net.minecraft.resources.ResourceLocation getTextureLocation(T entity);
    public void render(T entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int packedLight) {}
}
