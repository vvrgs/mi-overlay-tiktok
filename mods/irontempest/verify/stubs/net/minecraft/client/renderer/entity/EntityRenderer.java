package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public abstract class EntityRenderer<T extends Entity> {
    protected final EntityRenderDispatcher entityRenderDispatcher;
    protected float shadowRadius;

    protected EntityRenderer(EntityRendererProvider.Context context) {
        this.entityRenderDispatcher = null;
    }

    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {}

    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) { return false; }

    public abstract ResourceLocation getTextureLocation(T entity);
}
