package com.vvrgs.irontempest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.client.ModModelLayers;
import com.vvrgs.irontempest.entity.WarshipEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Nave orbital: hover con bob y roll, warp-in/out con escala elástica,
 * capa emisiva con pulso de carga y haz de energía con shader propio.
 */
public class WarshipRenderer extends EntityRenderer<WarshipEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/entity/warship.png");
    private static final ResourceLocation GLOW_TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/entity/warship_glow.png");
    /** Lightmap fullbright (mismo valor que usa EyesLayer vanilla). */
    private static final int FULL_BRIGHT = 15728640;
    // easeOutBack (overshoot elástico del warp-in).
    private static final float EASE_C1 = 1.70158F;
    private static final float EASE_C3 = EASE_C1 + 1.0F;
    private static final float WARP_TICKS = 8.0F;

    private final ModelPart root;
    private final ModelPart cannon;
    private final ModelPart cannonBarrel;
    private final ModelPart wingL;
    private final ModelPart wingR;
    private final ModelPart nacelleL;
    private final ModelPart nacelleR;
    private final ModelPart podL;
    private final ModelPart podR;

    // Poses base (ModelParts compartidos: posiciones mutadas se restauran).
    private final float wingLBaseZRot;
    private final float wingRBaseZRot;
    private final float nacelleLBaseZ;
    private final float nacelleRBaseZ;
    private final float podLBaseY;
    private final float podRBaseY;

    /**
     * Tick en el que cada entidad entró en WARP_OUT, detectado en render.
     * Claves débiles: las entradas se liberan cuando la entidad se descarta.
     */
    private final Map<WarshipEntity, Float> warpOutStart = new WeakHashMap<>();

    public WarshipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.root = context.bakeLayer(ModModelLayers.WARSHIP);
        this.cannon = this.root.getChild("cannon");
        this.cannonBarrel = this.cannon.getChild("cannon_barrel");
        this.wingL = this.root.getChild("wing_l");
        this.wingR = this.root.getChild("wing_r");
        this.nacelleL = this.root.getChild("nacelle_l");
        this.nacelleR = this.root.getChild("nacelle_r");
        this.podL = this.root.getChild("pod_l");
        this.podR = this.root.getChild("pod_r");
        this.wingLBaseZRot = this.wingL.zRot;
        this.wingRBaseZRot = this.wingR.zRot;
        this.nacelleLBaseZ = this.nacelleL.z;
        this.nacelleRBaseZ = this.nacelleR.z;
        this.podLBaseY = this.podL.y;
        this.podRBaseY = this.podR.y;
    }

    @Override
    public void render(WarshipEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float time = entity.tickCount + partialTick;
        byte phase = entity.getPhase();

        // Drift lissajous COMPARTIDO entre el modelo y el origen del haz: la
        // nave deriva sutilmente y el haz sigue soldado a la boca del cañón.
        Vec3 hoverOff = new Vec3(Mth.sin(time * 0.021F) * 0.05F,
                Mth.sin(time * 0.06F) * 0.15F, Mth.sin(time * 0.033F) * 0.05F);

        poseStack.pushPose();
        // Hover: bob senoidal + roll suave. La nave flota (root en y=0 del
        // modelo = centro de la entidad); mismo flip ZP(180) que el tanque
        // por la autoría +Y-abajo, sin offset de suelo.
        poseStack.translate(hoverOff.x, hoverOff.y, hoverOff.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.04F) * 1.2F));

        if (phase == WarshipEntity.PHASE_WARP_IN) {
            // La fase WARP_IN arranca con la entidad (tickCount 0): el propio
            // tickCount es el reloj. "Descompresión" del salto: X 0.05->1 con
            // overshoot elástico (easeOutBack), Y/Z estirados 3->1.
            float t = Mth.clamp(time / WARP_TICKS, 0.0F, 1.0F);
            float back = 1.0F + EASE_C3 * cube(t - 1.0F) + EASE_C1 * sq(t - 1.0F);
            float sx = 0.05F + 0.95F * back;
            float syz = 1.0F + 2.0F * cube(1.0F - t);
            poseStack.scale(sx, syz, syz);
        } else if (phase == WarshipEntity.PHASE_WARP_OUT) {
            // El cambio a WARP_OUT se detecta aquí (primer frame que lo ve).
            float start = this.warpOutStart.computeIfAbsent(entity, e -> (float) e.tickCount);
            float t = Mth.clamp((time - start) / WARP_TICKS, 0.0F, 1.0F);
            float sx = 1.0F - 0.95F * t * t;     // compresión hacia 0.05
            float syz = 1.0F + 2.0F * t * t;     // estirado previo al salto
            poseStack.scale(sx, syz, syz);
        }

        // IK del cañón hacia el beamTarget sincronizado. Mundo→modelo con la
        // convención YP(180−yaw)·ZP(180): mx=dx·cos+dz·sin, my=−dy,
        // mz=dx·sin−dz·cos (verificado contra cannonEmitter()).
        float charge = entity.getCharge();
        Vec3 beamTarget = entity.getBeamTarget();
        if (beamTarget.lengthSqr() > 1.0E-4D) {
            float yawRad = entityYaw * Mth.DEG_TO_RAD;
            Vec3 pivotWorld = entity.getPosition(partialTick).add(hoverOff)
                    .add(-Mth.sin(yawRad) * 0.375D, -0.5D, Mth.cos(yawRad) * 0.375D);
            Vec3 d = beamTarget.subtract(pivotWorld);
            double mx = d.x * Mth.cos(yawRad) + d.z * Mth.sin(yawRad);
            double my = -d.y;
            double mz = d.x * Mth.sin(yawRad) - d.z * Mth.cos(yawRad);
            this.cannon.yRot = (float) Math.atan2(mx, mz);
            // El cañón del modelo apunta +Y (recto abajo): la elevación es la
            // desviación de la vertical, limitada a ~34°.
            this.cannonBarrel.xRot = (float) Math.min(0.60D,
                    Math.atan2(Math.sqrt(mx * mx + mz * mz), my));
        } else {
            this.cannon.yRot = 0.0F;
            this.cannonBarrel.xRot = 0.0F;
        }
        // Giro del emisor acelerando con la carga (el azimut vive en cannon.yRot).
        this.cannonBarrel.yRot = time * (0.05F + 0.5F * charge);

        // Alas flexando a contrafase del bob (lag de masa); nacelles vibrando
        // con la carga; pods flotando lento.
        float flex = Mth.cos(time * 0.06F) * 0.02F;
        this.wingL.zRot = this.wingLBaseZRot + flex;
        this.wingR.zRot = this.wingRBaseZRot - flex;
        this.nacelleL.z = this.nacelleLBaseZ + Mth.sin(time * 0.9F) * 0.2F * charge;
        this.nacelleR.z = this.nacelleRBaseZ + Mth.sin(time * 0.9F + 3.1416F) * 0.2F * charge;
        this.podL.y = this.podLBaseY + Mth.sin(time * 0.13F) * 0.3F;
        this.podR.y = this.podRBaseY + Mth.sin(time * 0.13F + 1.7F) * 0.3F;

        VertexConsumer main = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.root.render(poseStack, main, packedLight, OverlayTexture.NO_OVERLAY);

        // Capa emisiva SIEMPRE; con carga alta se re-renderiza para "más
        // brillo" (eyes no permite modular alpha por vértice de forma útil).
        VertexConsumer glow = buffer.getBuffer(ModRenderTypes.glow(GLOW_TEXTURE));
        this.root.render(poseStack, glow, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        if (charge > 0.5F) {
            this.root.render(poseStack, glow, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        // Restaurar posiciones mutadas (rotaciones se escriben en absoluto).
        this.nacelleL.z = this.nacelleLBaseZ;
        this.nacelleR.z = this.nacelleRBaseZ;
        this.podL.y = this.podLBaseY;
        this.podR.y = this.podRBaseY;
        poseStack.popPose();

        if (phase == WarshipEntity.PHASE_BEAM || phase == WarshipEntity.PHASE_OVERLOAD) {
            renderBeam(entity, entityYaw, partialTick, hoverOff, poseStack, buffer,
                    phase == WarshipEntity.PHASE_OVERLOAD);
        }

        // Estrobos de punta de ala: doble flash blanco cada 24 gt.
        long gt = entity.level().getGameTime();
        if (gt % 24L < 2L) {
            renderStrobes(entity, entityYaw, hoverOff, poseStack, buffer);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderBeam(WarshipEntity entity, float entityYaw, float partialTick, Vec3 hoverOff,
                            PoseStack poseStack, MultiBufferSource buffer, boolean overload) {
        Vec3 target = entity.getBeamTarget();
        if (target.lengthSqr() < 1.0E-4) {
            return; // aún sin objetivo sincronizado
        }
        // Espacio local del render (poseStack en la posición de la entidad).
        // El haz nace en la BOCA del cañón animado: pivote (0,8,-6)/16 con el
        // drift aplicado + 0.875 bl (largo del cañón) en la dirección de tiro.
        Vec3 entityPos = entity.getPosition(partialTick);
        float yawRad = entityYaw * Mth.DEG_TO_RAD;
        Vec3 pivotLocal = hoverOff.add(-Mth.sin(yawRad) * 0.375D, -0.5D, Mth.cos(yawRad) * 0.375D);
        Vec3 end = target.subtract(entityPos);
        Vec3 aimDir = end.subtract(pivotLocal).normalize();
        Vec3 start = pivotLocal.add(aimDir.scale(0.875D));
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.01) {
            return;
        }
        Vec3 dir = delta.scale(1.0 / length);
        Vec3 upRef = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u1 = dir.cross(upRef).normalize();
        Vec3 u2 = dir.cross(u1).normalize();

        float time = entity.tickCount + partialTick;
        float gameTime = (float) (entity.level().getGameTime() % 24000L) + partialTick;
        float vLen = (float) (length / 8.0);

        VertexConsumer vc = buffer.getBuffer(ModRenderTypes.ENERGY_BEAM);
        Matrix4f pose = poseStack.last().pose();

        // Pase exterior: cilindro de 4 quads longitudinales cruzados,
        // radio pulsante; V scrollea hacia abajo (el haz "cae").
        float radius = 0.55F + Mth.sin(time * 0.5F) * 0.08F;
        if (overload) {
            radius *= 1.3F;
        }
        float v0 = -gameTime * 0.06F;
        beamPass(vc, pose, start, end, u1, u2, radius, v0, v0 + vLen, 150, 235, 255, 255);

        // Núcleo: más fino, scroll más rápido, casi blanco.
        float coreRadius = 0.22F + Mth.sin(time * 0.5F + 0.7F) * 0.03F;
        if (overload) {
            coreRadius *= 1.3F;
        }
        float v0c = -gameTime * 0.11F;
        beamPass(vc, pose, start, end, u1, u2, coreRadius, v0c, v0c + vLen, 235, 250, 255, 255);

        // Flash de impacto: billboard aditivo en el punto de contacto.
        poseStack.pushPose();
        poseStack.translate(end.x, end.y, end.z);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        Matrix4f flashPose = poseStack.last().pose();
        VertexConsumer add = buffer.getBuffer(ModRenderTypes.ADDITIVE_QUADS);
        float flash = 1.1F + Mth.sin(time * 0.9F) * 0.12F; // media diagonal (quad 2.2)
        flatQuad(add, flashPose, flash, 255, 210, 150, 220);
        flatQuad(add, flashPose, flash * 0.45F, 255, 250, 235, 255);
        poseStack.popPose();
    }

    /** 4 quads longitudinales a 0/45/90/135 grados alrededor del eje del haz. */
    private static void beamPass(VertexConsumer vc, Matrix4f pose, Vec3 start, Vec3 end,
                                 Vec3 u1, Vec3 u2, float radius, float v0, float v1,
                                 int red, int green, int blue, int alpha) {
        for (int k = 0; k < 4; k++) {
            double angle = k * (Math.PI / 4.0);
            Vec3 off = u1.scale(Math.cos(angle) * radius).add(u2.scale(Math.sin(angle) * radius));
            Vec3 a = start.subtract(off);
            Vec3 b = start.add(off);
            Vec3 c = end.add(off);
            Vec3 d = end.subtract(off);
            vc.vertex(pose, (float) a.x, (float) a.y, (float) a.z).color(red, green, blue, alpha).uv(0.0F, v0).endVertex();
            vc.vertex(pose, (float) b.x, (float) b.y, (float) b.z).color(red, green, blue, alpha).uv(1.0F, v0).endVertex();
            vc.vertex(pose, (float) c.x, (float) c.y, (float) c.z).color(red, green, blue, alpha).uv(1.0F, v1).endVertex();
            vc.vertex(pose, (float) d.x, (float) d.y, (float) d.z).color(red, green, blue, alpha).uv(0.0F, v1).endVertex();
        }
    }

    /** Estrobos blancos en las puntas de ala (billboard hacia cámara). */
    private void renderStrobes(WarshipEntity entity, float entityYaw, Vec3 hoverOff,
                               PoseStack poseStack, MultiBufferSource buffer) {
        float yawR = entityYaw * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(Mth.cos(yawR), 0.0D, Mth.sin(yawR));
        VertexConsumer add = buffer.getBuffer(ModRenderTypes.ADDITIVE_QUADS);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 p = hoverOff.add(right.scale(2.4D * s)).add(0.0D, 0.5D, 0.0D);
            poseStack.pushPose();
            poseStack.translate(p.x, p.y, p.z);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            flatQuad(add, poseStack.last().pose(), 0.14F, 255, 255, 255, 230);
            poseStack.popPose();
        }
    }

    /** Quad centrado en el origen del pose actual, en el plano XY. */
    private static void flatQuad(VertexConsumer vc, Matrix4f pose, float half,
                                 int red, int green, int blue, int alpha) {
        vc.vertex(pose, -half, -half, 0.0F).color(red, green, blue, alpha).uv(0.0F, 0.0F).endVertex();
        vc.vertex(pose, half, -half, 0.0F).color(red, green, blue, alpha).uv(1.0F, 0.0F).endVertex();
        vc.vertex(pose, half, half, 0.0F).color(red, green, blue, alpha).uv(1.0F, 1.0F).endVertex();
        vc.vertex(pose, -half, half, 0.0F).color(red, green, blue, alpha).uv(0.0F, 1.0F).endVertex();
    }

    private static float sq(float x) {
        return x * x;
    }

    private static float cube(float x) {
        return x * x * x;
    }

    @Override
    public boolean shouldRender(WarshipEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        // El haz mide ~40 bloques y sale del AABB: no cullear de cerca.
        if (entity.distanceToSqr(camX, camY, camZ) < 16384.0) {
            return true;
        }
        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    @Override
    public ResourceLocation getTextureLocation(WarshipEntity entity) {
        return TEXTURE;
    }
}
