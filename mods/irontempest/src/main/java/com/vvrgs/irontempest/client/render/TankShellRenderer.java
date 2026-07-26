package com.vvrgs.irontempest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.client.ModModelLayers;
import com.vvrgs.irontempest.entity.TankShellEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Obús de tanque: modelo orientado por la velocidad (rotaciones que el
 * servidor deriva en updateRotationFromVelocity) + trazador aditivo.
 */
public class TankShellRenderer extends EntityRenderer<TankShellEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/entity/tank_shell.png");

    private final ModelPart root;

    public TankShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.root = context.bakeLayer(ModModelLayers.TANK_SHELL);
    }

    @Override
    public void render(TankShellEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        poseStack.pushPose();
        /*
         * Proyectiles: yaw = atan2(vx, vz) (convención flecha, signo
         * OPUESTO al yaw vanilla de entidad viva), por eso YP(180+yaw) y
         * no 180-yaw: lleva la nariz -Z del modelo a (sin yaw, cos yaw).
         * XP(+pitch) sube la nariz porque guardamos pitch positivo=arriba
         * (atan2(vy, horiz)) y XP positivo lleva -Z hacia +Y. ZP(180)
         * cierra la convención de autoría +Y-abajo (= scale(-1,-1,1)).
         */
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

        VertexConsumer main = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.root.render(poseStack, main, packedLight, OverlayTexture.NO_OVERLAY);

        renderTracer(poseStack, buffer);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** Trazador naranja: 2 quads cruzados 1.2 bloques hacia atrás (+Z local). */
    private static void renderTracer(PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer vc = buffer.getBuffer(ModRenderTypes.ADDITIVE_QUADS);
        Matrix4f pose = poseStack.last().pose();
        float w = 0.06F;
        float z0 = 0.25F;
        float z1 = z0 + 1.2F;
        // Horizontal (plano XZ): cabeza brillante -> cola transparente.
        vc.vertex(pose, -w, 0.0F, z0).color(255, 160, 60, 210).uv(0.0F, 0.0F).endVertex();
        vc.vertex(pose, w, 0.0F, z0).color(255, 160, 60, 210).uv(1.0F, 0.0F).endVertex();
        vc.vertex(pose, w, 0.0F, z1).color(255, 60, 10, 0).uv(1.0F, 1.0F).endVertex();
        vc.vertex(pose, -w, 0.0F, z1).color(255, 60, 10, 0).uv(0.0F, 1.0F).endVertex();
        // Vertical (plano YZ).
        vc.vertex(pose, 0.0F, -w, z0).color(255, 160, 60, 210).uv(0.0F, 0.0F).endVertex();
        vc.vertex(pose, 0.0F, w, z0).color(255, 160, 60, 210).uv(1.0F, 0.0F).endVertex();
        vc.vertex(pose, 0.0F, w, z1).color(255, 60, 10, 0).uv(1.0F, 1.0F).endVertex();
        vc.vertex(pose, 0.0F, -w, z1).color(255, 60, 10, 0).uv(0.0F, 1.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(TankShellEntity entity) {
        return TEXTURE;
    }
}
