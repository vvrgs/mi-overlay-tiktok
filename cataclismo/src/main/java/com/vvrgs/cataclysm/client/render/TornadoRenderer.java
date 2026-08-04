package com.vvrgs.cataclysm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.entity.TornadoEntity;
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
 * Embudo procedural: 8 segmentos apilados girando a velocidades distintas
 * (mas rapido abajo) con wobble creciente hacia la boca.
 *
 * Reglas de renderer aprendidas a golpes: los ModelPart se hornean UNA vez y
 * se COMPARTEN entre entidades — toda pose mutada se escribe en ABSOLUTO
 * (base + offset), jamas '+='. Spin puro SOLO en yRot (orden ZYX: un yRot
 * junto a xRot precesiona).
 */
public class TornadoRenderer extends EntityRenderer<TornadoEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(Cataclysm.MODID, "tornado"), "main");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Cataclysm.MODID, "textures/entity/tornado.png");

    private static final int SEGMENTS = 8;
    private static final float MODEL_HEIGHT_BLOCKS = 6.0F;
    private static final float WORLD_HEIGHT_BLOCKS = 14.0F;

    private final ModelPart[] segments = new ModelPart[SEGMENTS];
    private final float[] baseX = new float[SEGMENTS];
    private final float[] baseY = new float[SEGMENTS];
    private final float[] baseZ = new float[SEGMENTS];

    public TornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = context.bakeLayer(LAYER);
        for (int i = 0; i < SEGMENTS; i++) {
            segments[i] = root.getChild("seg" + i);
            // poses base capturadas en el ctor: se restauran en cada frame
            baseX[i] = segments[i].x;
            baseY[i] = segments[i].y;
            baseZ[i] = segments[i].z;
        }
    }

    @Override
    public void render(TornadoEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float intensity = entity.getIntensity();
        if (intensity < 0.02F) {
            return;
        }
        float age = entity.tickCount + partialTick;
        float scale = (WORLD_HEIGHT_BLOCKS / MODEL_HEIGHT_BLOCKS)
                * (0.25F + 0.75F * intensity);

        poseStack.pushPose();
        poseStack.translate(0.0D, MODEL_HEIGHT_BLOCKS * scale, 0.0D);
        poseStack.scale(-scale, -scale, scale);

        for (int i = 0; i < SEGMENTS; i++) {
            ModelPart seg = segments[i];
            // spin puro en yRot: abajo mas rapido; ABSOLUTO, no '+='
            seg.yRot = age * (0.55F - i * 0.045F) + i * 1.1F;
            float wobble = (1.5F + i * 0.6F) * intensity;
            seg.x = baseX[i] + Mth.sin(age * 0.13F + i * 0.7F) * wobble;
            seg.z = baseZ[i] + Mth.cos(age * 0.11F + i * 0.7F) * wobble;
            seg.y = baseY[i];
        }

        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        float alpha = 0.55F + 0.35F * intensity;
        for (ModelPart seg : segments) {
            seg.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, alpha);
        }
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(TornadoEntity entity) {
        return TEXTURE;
    }
}
