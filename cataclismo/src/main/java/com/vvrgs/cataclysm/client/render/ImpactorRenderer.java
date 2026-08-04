package com.vvrgs.cataclysm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.entity.ImpactorEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * El impactor planetario: nucleo rocoso a escala + cascara de plasma
 * fullbright pulsante. Se ve venir — render a escala real, no una particula.
 */
public class ImpactorRenderer extends EntityRenderer<ImpactorEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(Cataclysm.MODID, "impactor"), "main");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Cataclysm.MODID, "textures/entity/impactor.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(Cataclysm.MODID, "textures/entity/impactor_glow.png");

    private final ModelPart core;
    private final ModelPart shell;

    public ImpactorRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = context.bakeLayer(LAYER);
        this.core = root.getChild("core");
        this.shell = root.getChild("shell");
    }

    @Override
    public void render(ImpactorEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float size = entity.getSize();

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 7.0F));
        poseStack.scale(-size, -size, size);

        VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        core.render(poseStack, base, packedLight, OverlayTexture.NO_OVERLAY);

        // cascara de plasma pulsante, fullbright
        float pulse = 1.0F + 0.06F * Mth.sin(age * 0.7F);
        poseStack.pushPose();
        poseStack.scale(pulse, pulse, pulse);
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        shell.render(poseStack, glow, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ImpactorEntity entity) {
        return TEXTURE;
    }
}
