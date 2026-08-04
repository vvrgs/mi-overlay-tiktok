package com.vvrgs.cataclysm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.entity.VolcanicBombEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Bomba volcanica: nucleo de magma con placas de basalto, spin caotico, glow. */
public class VolcanicBombRenderer extends EntityRenderer<VolcanicBombEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(Cataclysm.MODID, "volcanic_bomb"), "main");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Cataclysm.MODID, "textures/entity/volcanic_bomb.png");
    private static final ResourceLocation GLOW =
            new ResourceLocation(Cataclysm.MODID, "textures/entity/volcanic_bomb_glow.png");

    private final ModelPart root;

    public VolcanicBombRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.root = context.bakeLayer(LAYER);
    }

    @Override
    public void render(VolcanicBombEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 23.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 11.0F));
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        root.render(poseStack, base, packedLight, OverlayTexture.NO_OVERLAY);
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        root.render(poseStack, glow, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(VolcanicBombEntity entity) {
        return TEXTURE;
    }
}
