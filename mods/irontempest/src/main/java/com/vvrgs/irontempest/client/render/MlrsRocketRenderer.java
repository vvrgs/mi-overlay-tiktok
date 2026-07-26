package com.vvrgs.irontempest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.client.ModModelLayers;
import com.vvrgs.irontempest.entity.MlrsRocketEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Cohete MLRS: modelo simple orientado por la velocidad. */
public class MlrsRocketRenderer extends EntityRenderer<MlrsRocketEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/entity/mlrs_rocket.png");

    private final ModelPart root;

    public MlrsRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.root = context.bakeLayer(ModModelLayers.MLRS_ROCKET);
    }

    @Override
    public void render(MlrsRocketEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.pushPose();
        // Misma convención que TankShellRenderer: yaw de proyectil
        // (atan2(vx,vz)) => YP(180+yaw); pitch positivo=arriba => XP(+pitch);
        // ZP(180) por la autoría +Y-abajo.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

        VertexConsumer main = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.root.render(poseStack, main, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MlrsRocketEntity entity) {
        return TEXTURE;
    }
}
