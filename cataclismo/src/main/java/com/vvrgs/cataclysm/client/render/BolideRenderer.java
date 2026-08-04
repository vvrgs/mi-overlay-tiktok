package com.vvrgs.cataclysm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.entity.BolideEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Bolido: roca con fragmentos, spin sobre su eje de vuelo, grietas
 * incandescentes fullbright (capa eyes con _glow).
 */
public class BolideRenderer extends EntityRenderer<BolideEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(Cataclysm.MODID, "bolide"), "main");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Cataclysm.MODID, "textures/entity/bolide.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(Cataclysm.MODID, "textures/entity/bolide_glow.png");

    private final ModelPart root;

    public BolideRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.root = context.bakeLayer(LAYER);
    }

    @Override
    public void render(BolideEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float size = entity.getSize();

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        // orientacion de vuelo (yaw/pitch del server) + spin de roll propio.
        // composicion via PoseStack (matrices), no eulers de ModelPart: sin precesion.
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 21.0F));
        poseStack.scale(-size, -size, size);

        VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        root.render(poseStack, base, packedLight, OverlayTexture.NO_OVERLAY);
        // grietas incandescentes: fullbright encima
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        root.render(poseStack, glow, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(BolideEntity entity) {
        return TEXTURE;
    }
}
