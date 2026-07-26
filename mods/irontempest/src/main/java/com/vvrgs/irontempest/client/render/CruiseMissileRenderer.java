package com.vvrgs.irontempest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.client.ModModelLayers;
import com.vvrgs.irontempest.entity.CruiseMissileEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Misil de crucero: nariz -Z orientada por las rotaciones de la entidad;
 * en BOOST vibra (jitter de motor a plena potencia).
 */
public class CruiseMissileRenderer extends EntityRenderer<CruiseMissileEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/entity/cruise_missile.png");

    private final ModelPart root;

    public CruiseMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.root = context.bakeLayer(ModModelLayers.CRUISE_MISSILE);
    }

    @Override
    public void render(CruiseMissileEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.pushPose();
        /*
         * yaw del proyectil = atan2(vx, vz) (signo opuesto al yaw vanilla
         * de entidad viva) => YP(180+yaw) apunta la nariz -Z del modelo a
         * (sin yaw, cos yaw). Nuestro xRot es atan2(vy, horiz): POSITIVO
         * cuando sube (al contrario que el xRot vanilla, positivo=abajo);
         * XP(+pitch) lleva la nariz -Z hacia +Y => nariz arriba cuando el
         * misil asciende, correcto. ZP(180) = convención +Y-abajo.
         */
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

        if (entity.getPhase() == CruiseMissileEntity.PHASE_BOOST) {
            // Vibración de despegue (jitter determinista, amplitud 0.01).
            float time = entity.tickCount + partialTick;
            poseStack.translate(
                    Mth.sin(time * 11.3F) * 0.01F,
                    Mth.sin(time * 13.7F + 0.9F) * 0.01F,
                    Mth.sin(time * 9.1F + 2.1F) * 0.01F);
        }

        VertexConsumer main = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.root.render(poseStack, main, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CruiseMissileEntity entity) {
        return TEXTURE;
    }
}
