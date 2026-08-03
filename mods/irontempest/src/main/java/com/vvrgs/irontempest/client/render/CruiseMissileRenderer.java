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
 * BANKING real en los giros (roll por delta de yaw), superficies de control
 * deflectando, flutter terminal, chuffing de tobera en BOOST y glow emisivo.
 */
public class CruiseMissileRenderer extends EntityRenderer<CruiseMissileEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/entity/cruise_missile.png");
    private static final ResourceLocation GLOW_TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/entity/cruise_missile_glow.png");
    /** Lightmap fullbright (mismo valor que usa EyesLayer vanilla). */
    private static final int FULL_BRIGHT = 15728640;

    private final ModelPart root;
    private final ModelPart[] fins = new ModelPart[4];
    private final ModelPart[] midfins = new ModelPart[4];
    private final ModelPart nozzle;
    private final float nozzleBaseZ;

    public CruiseMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.root = context.bakeLayer(ModModelLayers.CRUISE_MISSILE);
        for (int i = 0; i < 4; i++) {
            this.fins[i] = this.root.getChild("fin" + i);
            this.midfins[i] = this.root.getChild("midfin" + i);
        }
        this.nozzle = this.root.getChild("nozzle");
        this.nozzleBaseZ = this.nozzle.z;
    }

    @Override
    public void render(CruiseMissileEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float time = entity.tickCount + partialTick;
        byte phase = entity.getPhase();

        // FÍSICA visual: banking — el misil se LADEA en los giros (roll
        // proporcional al delta de yaw; el weave terminal lo hace oscilar).
        float yawDelta = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
        float roll = Mth.clamp(yawDelta * 2.5F, -25.0F, 25.0F);
        float pitchDelta = entity.getXRot() - entity.xRotO;

        poseStack.pushPose();
        /*
         * yaw del proyectil = atan2(vx, vz) (signo opuesto al yaw vanilla
         * de entidad viva) => YP(180+yaw) apunta la nariz -Z del modelo a
         * (sin yaw, cos yaw). Nuestro xRot es atan2(vy, horiz): POSITIVO
         * cuando sube (al contrario que el xRot vanilla, positivo=abajo);
         * XP(+pitch) lleva la nariz -Z hacia +Y => nariz arriba cuando el
         * misil asciende, correcto. ZP(180) = convención +Y-abajo; el roll
         * de banking va DENTRO de ese mismo ZP (jamás antes del XP).
         */
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F + roll));

        if (phase == CruiseMissileEntity.PHASE_BOOST) {
            // Vibración de despegue (jitter determinista, amplitud 0.01).
            poseStack.translate(
                    Mth.sin(time * 11.3F) * 0.01F,
                    Mth.sin(time * 13.7F + 0.9F) * 0.01F,
                    Mth.sin(time * 9.1F + 2.1F) * 0.01F);
        }

        // Superficies de control: cada aleta deflecta alrededor de SU charnela
        // (el zRot horneado 0/90/180/270 convierte yRot en el eje propio).
        float dy = Mth.clamp(yawDelta * 1.2F, -10.0F, 10.0F) * Mth.DEG_TO_RAD;
        float dp = Mth.clamp(pitchDelta * 1.2F, -10.0F, 10.0F) * Mth.DEG_TO_RAD;
        this.fins[0].yRot = dy;
        this.fins[2].yRot = -dy;   // timón: pareja opuesta por el zRot 180
        this.fins[1].yRot = dp;
        this.fins[3].yRot = -dp;   // elevador
        for (int i = 0; i < 4; i++) {
            // Flutter aerodinámico solo en el picado terminal.
            this.midfins[i].yRot = phase == CruiseMissileEntity.PHASE_TERMINAL
                    ? Mth.sin(time * 2.1F + i) * 0.026F : 0.0F;
        }
        // Chuffing de tobera en BOOST (pulso del motor a plena potencia).
        this.nozzle.z = phase == CruiseMissileEntity.PHASE_BOOST
                ? this.nozzleBaseZ + Mth.sin(time * 9.7F) * 0.25F : this.nozzleBaseZ;

        VertexConsumer main = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.root.render(poseStack, main, packedLight, OverlayTexture.NO_OVERLAY);

        // Glow (tobera + sensor de nariz): siempre en BOOST; en TERMINAL
        // alterna cada tick = flicker de afterburner.
        boolean glowOn = phase == CruiseMissileEntity.PHASE_BOOST
                || phase == CruiseMissileEntity.PHASE_CRUISE
                || (entity.tickCount & 1) == 0;
        if (glowOn) {
            VertexConsumer glow = buffer.getBuffer(ModRenderTypes.glow(GLOW_TEXTURE));
            this.root.render(poseStack, glow, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }

        this.nozzle.z = this.nozzleBaseZ; // restaurar la única posición mutada
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CruiseMissileEntity entity) {
        return TEXTURE;
    }
}
