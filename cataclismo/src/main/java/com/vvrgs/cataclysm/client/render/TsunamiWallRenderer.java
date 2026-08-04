package com.vvrgs.cataclysm.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.entity.TsunamiWallEntity;
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
 * Muro de agua: la lamina usa el RenderType de scroll (core shader propio:
 * las UVs fluyen hacia arriba con GameTime — la textura es EXACTAMENTE
 * periodica en Y). La voluta de la cresta cabecea; la espuma respira.
 */
public class TsunamiWallRenderer extends EntityRenderer<TsunamiWallEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(Cataclysm.MODID, "tsunami_wall"), "main");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Cataclysm.MODID, "textures/entity/tsunami_wall.png");
    /** Textura dedicada del scroll: TODO el lienzo es agua periodica en Y
     *  (el fract() del shader tesela la textura completa). */
    private static final ResourceLocation SHEET =
            new ResourceLocation(Cataclysm.MODID, "textures/entity/tsunami_sheet.png");

    private static final float SCALE = 2.0F;
    private static final float MODEL_HEIGHT_BLOCKS = 5.5F;

    private final ModelPart body;
    private final ModelPart curl;
    private final ModelPart foam;
    private final float curlBaseY;
    private final float foamBaseY;

    public TsunamiWallRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = context.bakeLayer(LAYER);
        this.body = root.getChild("body");
        this.curl = root.getChild("curl");
        this.foam = root.getChild("foam");
        this.curlBaseY = curl.y;
        this.foamBaseY = foam.y;
    }

    @Override
    public void render(TsunamiWallEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0D, MODEL_HEIGHT_BLOCKS * SCALE, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        poseStack.scale(-SCALE, -SCALE, SCALE);

        // cabeceo de la cresta: escritura ABSOLUTA sobre la base capturada
        curl.y = curlBaseY + Mth.sin(age * 0.18F) * 1.5F;
        foam.y = foamBaseY + Mth.sin(age * 0.22F + 1.0F) * 1.2F;

        // lamina de agua: UV scroll por shader (GameTime)
        VertexConsumer water = buffers.getBuffer(CataclysmRenderTypes.uvScroll(SHEET));
        body.render(poseStack, water, packedLight, OverlayTexture.NO_OVERLAY);
        curl.render(poseStack, water, packedLight, OverlayTexture.NO_OVERLAY);
        // espuma: translucida normal
        VertexConsumer foamBuffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        foam.render(poseStack, foamBuffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(TsunamiWallEntity entity) {
        return TEXTURE;
    }
}
