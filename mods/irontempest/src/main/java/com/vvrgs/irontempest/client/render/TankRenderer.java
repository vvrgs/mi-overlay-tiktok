package com.vvrgs.irontempest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.client.ModModelLayers;
import com.vvrgs.irontempest.entity.TankEntity;
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
 * Renderer del tanque: torreta/cañón interpolados, retroceso de resorte,
 * vibración de ralentí, antenas con inercia, capa de faros emisiva y
 * designador láser aditivo cuando apunta.
 */
public class TankRenderer extends EntityRenderer<TankEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/entity/tank.png");
    private static final ResourceLocation GLOW_TEXTURE =
            new ResourceLocation(IronTempest.MODID, "textures/entity/tank_glow.png");
    /** Lightmap fullbright (mismo valor que usa EyesLayer vanilla). */
    private static final int FULL_BRIGHT = 15728640;
    private static final float LASER_RANGE = 40.0F;

    private final ModelPart root;
    private final ModelPart turret;
    private final ModelPart barrel;
    private final ModelPart[] wheels = new ModelPart[10];
    private final ModelPart ant0;
    private final ModelPart ant1;
    private final ModelPart beltL;
    private final ModelPart beltR;
    private final ModelPart cupola;
    private final ModelPart mg;
    private final ModelPart smokeL;
    private final ModelPart smokeR;

    // Poses base (los ModelPart están compartidos entre entidades: siempre
    // se anima desde la base capturada y se restaura tras el render).
    private final float barrelBaseZ;
    private final float ant0BaseZRot;
    private final float ant1BaseXRot;
    private final float beltLBaseZ;
    private final float beltRBaseZ;
    private final float[] wheelBaseY = new float[10];
    private final float smokeLBaseXRot;
    private final float smokeRBaseXRot;

    public TankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.9F;
        this.root = context.bakeLayer(ModModelLayers.WAR_TANK);
        // Valida el nombre (getChild lanza si la geometría cambia).
        this.root.getChild("hull");
        this.turret = this.root.getChild("turret");
        this.barrel = this.turret.getChild("barrel");
        ModelPart trackL = this.root.getChild("track_l");
        ModelPart trackR = this.root.getChild("track_r");
        for (int i = 0; i < 5; i++) {
            this.wheels[i] = trackL.getChild("wheel_l" + i);
            this.wheels[5 + i] = trackR.getChild("wheel_r" + i);
        }
        this.beltL = trackL.getChild("belt_l");
        this.beltR = trackR.getChild("belt_r");
        this.ant0 = this.turret.getChild("ant0");
        this.ant1 = this.turret.getChild("ant1");
        this.cupola = this.turret.getChild("cupola");
        this.mg = this.turret.getChild("mg");
        this.smokeL = this.turret.getChild("smoke_l");
        this.smokeR = this.turret.getChild("smoke_r");
        this.barrelBaseZ = this.barrel.z;
        this.ant0BaseZRot = this.ant0.zRot;
        this.ant1BaseXRot = this.ant1.xRot;
        this.beltLBaseZ = this.beltL.z;
        this.beltRBaseZ = this.beltR.z;
        for (int i = 0; i < 10; i++) {
            this.wheelBaseY[i] = this.wheels[i].y;
        }
        this.smokeLBaseXRot = this.smokeL.xRot;
        this.smokeRBaseXRot = this.smokeR.xRot;
    }

    /** easeOutBack clásico: pequeño overshoot al final (lanzahumos erguidos). */
    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    @Override
    public void render(TankEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float time = entity.tickCount + partialTick;
        float fireT = entity.ticksSinceFire(partialTick);
        boolean engine = entity.isEngineOn();

        poseStack.pushPose();
        /*
         * Transformación (patrón LivingRenderer adaptado): el modelo está
         * autorizado con +Y hacia ABAJO, frente -Z y suelo en y=24 (=1.5
         * bloques, ModelPart divide entre 16). ZP(180) es exactamente la
         * misma matriz que el scale(-1,-1,1) de LivingRenderer (invierte
         * X e Y), con lo que el suelo del modelo (y=+1.5 tras /16) pasa a
         * y=-1.5; el translate(0,1.5,0) previo lo devuelve a y=0 de la
         * entidad: el tanque apoya en el suelo. YP(180-yaw): con yaw=0 el
         * frente -Z del modelo mira a +Z (sur), convención vanilla.
         */
        double bobY = engine ? Math.sin(time * 0.7) * 0.006 : 0.0;
        poseStack.translate(0.0, 1.5 + bobY, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        // Física de retroceso del CASCO: el disparo mece todo el tanque sobre la
        // suspensión (cabeceo con rebote amortiguado, máx ~1.6°) + cabeceo por
        // ACELERACIÓN real (morro arriba al arrancar, picado al frenar).
        float rock = fireT < 22.0F
                ? 1.6F * (float) (Math.exp(-fireT * 0.22) * Math.cos(fireT * 0.55)) : 0.0F;
        float accelPitch = Mth.clamp(entity.accelSmooth * 650.0F, -3.0F, 3.0F);
        if (rock != 0.0F || accelPitch != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(rock + accelPitch));
        }

        // a) Torreta: yaw absoluto interpolado, relativo al casco. Mismo
        //    signo que la cabeza de un mob (yRot = (head - body) en rad).
        float turretYaw = Mth.rotLerp(partialTick, entity.turretYawO, entity.getTurretYaw());
        this.turret.yRot = Mth.wrapDegrees(turretYaw - entityYaw) * Mth.DEG_TO_RAD;

        // b) Cañón: pitch interpolado (positivo=arriba; xRot positivo baja
        //    el extremo -Z, de ahí el signo) + retroceso de resorte.
        float barrelPitch = Mth.lerp(partialTick, entity.barrelPitchO, entity.getBarrelPitch());
        this.barrel.xRot = -barrelPitch * Mth.DEG_TO_RAD;
        float recoil = 0.0F;
        if (fireT < 12.0F) {
            if (fireT < 1.5F) {
                // Golpe seco: 0 -> 5.5 unidades hacia atrás (+Z) en 1.5 ticks.
                recoil = 5.5F * (fireT / 1.5F);
            } else {
                // Retorno con rebote amortiguado.
                float t = fireT - 1.5F;
                recoil = 5.5F * (float) (Math.exp(-t * 0.35) * Math.cos(t * 0.9));
                if (recoil < 0.0F) {
                    recoil *= 0.35F; // el rebote hacia delante es más corto
                }
            }
        }
        this.barrel.z = this.barrelBaseZ + recoil;

        // c) Ruedas RODANDO por distancia real acumulada + suspensión por rueda;
        //    vibración de ralentí solo con el tanque quieto y el motor encendido.
        float wheelRot = Mth.lerp(partialTick, entity.wheelRotO, entity.wheelRot);
        float speed = (float) entity.horizontalSpeed();
        float speedFactor = Mth.clamp(speed / 0.055F, 0.0F, 1.0F);
        boolean moving = speed > 0.005F;
        for (int i = 0; i < this.wheels.length; i++) {
            this.wheels[i].xRot = moving ? wheelRot
                    : (engine ? Mth.sin(time * 0.9F + i) * 0.02F : 0.0F);
            this.wheels[i].y = this.wheelBaseY[i]
                    + Mth.sin(wheelRot * 0.9F + i * 1.7F) * 0.35F * speedFactor;
        }

        // c2) SCROLL de orugas (fake): el material de la cinta es periódico cada
        //     3 unidades — un sawtooth de un período exacto es invisible. Con
        //     diferencial por lado: al girar, una cinta avanza y la otra retrocede.
        float beltLOff = Mth.lerp(partialTick, entity.beltLO, entity.beltL);
        float beltROff = Mth.lerp(partialTick, entity.beltRO, entity.beltR);
        this.beltL.z = this.beltLBaseZ + ((beltLOff % 3.0F) + 3.0F) % 3.0F;
        this.beltR.z = this.beltRBaseZ + ((beltROff % 3.0F) + 3.0F) % 3.0F;

        // d) Antenas: viento suave + inercia amplificada tras el disparo.
        float sway = fireT < 20.0F ? 3.0F : 1.0F;
        this.ant0.zRot = this.ant0BaseZRot
                + (Mth.sin(time * 0.13F) * 0.035F + Mth.sin(time * 0.31F) * 0.012F) * sway;
        this.ant1.xRot = this.ant1BaseXRot
                + (Mth.sin(time * 0.17F + 1.3F) * 0.03F + Mth.sin(time * 0.41F) * 0.01F) * sway;

        // e) Cúpula + ametralladora ESCANEANDO el horizonte; convergen a 0
        //    (sin pop) cuando el tanque apunta de verdad.
        float scan = Mth.sin(time * 0.045F) * 0.5F * entity.scanAmp;
        this.mg.yRot = scan;
        this.cupola.yRot = scan * 0.35F;

        // f) Lanzahumos: se yerguen con overshoot al entrar en retirada.
        if (entity.leaveStart >= 0) {
            float t = Math.min(1.0F, (entity.tickCount - entity.leaveStart + partialTick) / 12.0F);
            float pop = easeOutBack(t) * 0.20F;
            this.smokeL.xRot = this.smokeLBaseXRot - pop;
            this.smokeR.xRot = this.smokeRBaseXRot - pop;
        } else {
            this.smokeL.xRot = this.smokeLBaseXRot;
            this.smokeR.xRot = this.smokeRBaseXRot;
        }

        VertexConsumer main = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.root.render(poseStack, main, packedLight, OverlayTexture.NO_OVERLAY);

        // Capa de faros emisiva (fullbright).
        VertexConsumer glow = buffer.getBuffer(ModRenderTypes.glow(GLOW_TEXTURE));
        this.root.render(poseStack, glow, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

        // Restaura las poses compartidas (toda POSICIÓN mutada vuelve a base).
        this.barrel.z = this.barrelBaseZ;
        this.beltL.z = this.beltLBaseZ;
        this.beltR.z = this.beltRBaseZ;
        for (int i = 0; i < this.wheels.length; i++) {
            this.wheels[i].y = this.wheelBaseY[i];
        }
        poseStack.popPose();

        // Designador láser en espacio local sin rotar (ejes de mundo).
        if (entity.isAiming()) {
            renderLaser(entity, partialTick, poseStack, buffer);
        }

        // Beacon rojo de la antena: doble flash real cada 1.5 s (un blink por
        // textura no puede apagarse — el pase glow renderiza el root entero).
        long phase = entity.level().getGameTime() % 30L;
        if (phase < 2L || (phase >= 5L && phase < 7L)) {
            renderBeacon(entity, partialTick, poseStack, buffer);
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderLaser(TankEntity entity, float partialTick, PoseStack poseStack,
                             MultiBufferSource buffer) {
        // El poseStack está en la posición interpolada de la entidad:
        // pasa el muzzle de mundo a espacio local restando esa posición.
        // Interpolar torreta/cañón con partialTick: el extremo del láser a 40 bl
        // salta ~1.5 bl por tick si se usan los valores crudos sincronizados.
        float yaw = Mth.rotLerp(partialTick, entity.turretYawO, entity.getTurretYaw()) * Mth.DEG_TO_RAD;
        float pitch = Mth.lerp(partialTick, entity.barrelPitchO, entity.getBarrelPitch()) * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        Vec3 origin = new Vec3(0.0D, 1.72D, 0.0D).add(dir.scale(2.75D));
        Vec3 end = origin.add(dir.scale(LASER_RANGE));

        Vec3 upRef = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 u1 = dir.cross(upRef).normalize();
        Vec3 u2 = dir.cross(u1).normalize();

        VertexConsumer vc = buffer.getBuffer(ModRenderTypes.ADDITIVE_QUADS);
        Matrix4f pose = poseStack.last().pose();

        // Dos quads cruzados (billboard axial) de grosor 0.03, rojo.
        Vec3 p1 = u1.scale(0.03);
        Vec3 p2 = u2.scale(0.03);
        quad(vc, pose, origin.subtract(p1), origin.add(p1), end.add(p1), end.subtract(p1),
                255, 40, 40, 160);
        quad(vc, pose, origin.subtract(p2), origin.add(p2), end.add(p2), end.subtract(p2),
                255, 40, 40, 160);

        // Destello en el origen: quad perpendicular al haz de 0.15.
        Vec3 f1 = u1.scale(0.15);
        Vec3 f2 = u2.scale(0.15);
        quad(vc, pose,
                origin.subtract(f1).subtract(f2), origin.add(f1).subtract(f2),
                origin.add(f1).add(f2), origin.subtract(f1).add(f2),
                255, 110, 90, 200);
    }

    /** Luz anticolisión en la punta de ant1: dos quads cruzados aditivos que
     *  siguen la rotación de la torreta (espacio local sin rotar, como el láser). */
    private void renderBeacon(TankEntity entity, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer) {
        float tau = Mth.rotLerp(partialTick, entity.turretYawO, entity.getTurretYaw()) * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(tau), 0.0D, Mth.cos(tau));
        Vec3 right = new Vec3(Mth.cos(tau), 0.0D, Mth.sin(tau));
        // ant1: pivote modelo (8, 0, 10) (torreta (0,7,-2) + (8,-7,12)), punta a
        // −10 u → altura (24+10)/16; x/z /16 rotados por el yaw de torreta.
        Vec3 tip = right.scale(0.5D).add(facing.scale(-0.625D)).add(0.0D, 2.15D, 0.0D);
        VertexConsumer vc = buffer.getBuffer(ModRenderTypes.ADDITIVE_QUADS);
        Matrix4f pose = poseStack.last().pose();
        double s = 0.09D;
        quad(vc, pose, tip.add(-s, -s, 0.0D), tip.add(s, -s, 0.0D),
                tip.add(s, s, 0.0D), tip.add(-s, s, 0.0D), 255, 45, 40, 220);
        quad(vc, pose, tip.add(0.0D, -s, -s), tip.add(0.0D, -s, s),
                tip.add(0.0D, s, s), tip.add(0.0D, s, -s), 255, 45, 40, 220);
    }

    private static void quad(VertexConsumer vc, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             int red, int green, int blue, int alpha) {
        vc.vertex(pose, (float) a.x, (float) a.y, (float) a.z).color(red, green, blue, alpha).uv(0.0F, 0.0F).endVertex();
        vc.vertex(pose, (float) b.x, (float) b.y, (float) b.z).color(red, green, blue, alpha).uv(1.0F, 0.0F).endVertex();
        vc.vertex(pose, (float) c.x, (float) c.y, (float) c.z).color(red, green, blue, alpha).uv(1.0F, 1.0F).endVertex();
        vc.vertex(pose, (float) d.x, (float) d.y, (float) d.z).color(red, green, blue, alpha).uv(0.0F, 1.0F).endVertex();
    }

    @Override
    public boolean shouldRender(TankEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        // El láser mide 40 bloques: no cortarlo cuando el AABB queda fuera.
        if (entity.isAiming() && entity.distanceToSqr(camX, camY, camZ) < 4096.0) {
            return true;
        }
        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    @Override
    public ResourceLocation getTextureLocation(TankEntity entity) {
        return TEXTURE;
    }
}
