package com.vvrgs.cataclysm.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.vvrgs.cataclysm.fx.SkyFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Objetos celestes via RenderLevelStageEvent AFTER_SKY: el meteorito del
 * /impacto que CRECE durante segundos, la estela de entrada y el agujero
 * negro con disco de acrecion del apocalipsis.
 *
 * Depth-test OFF y sin fog para que se lea a cualquier distancia.
 */
public final class SkyRenderer {

    private static final float SKY_DISTANCE = 120.0F;

    public static void render(RenderLevelStageEvent event) {
        boolean approach = SkyFxState.isActive(SkyFx.IMPACT_APPROACH);
        boolean entry = SkyFxState.isActive(SkyFx.ATMOSPHERE_ENTRY);
        boolean blackHole = SkyFxState.isActive(SkyFx.BLACK_HOLE);
        if (!approach && !entry && !blackHole) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float time = (mc.level.getGameTime() % 24000L) + event.getPartialTick();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        Matrix4f matrix = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        if (approach) {
            renderApproach(matrix, SkyFxState.progress(SkyFx.IMPACT_APPROACH), time);
        }
        if (entry) {
            renderEntryStreak(matrix, SkyFxState.progress(SkyFx.ATMOSPHERE_ENTRY));
        }
        if (blackHole) {
            renderBlackHole(matrix, time, SkyFxState.intensity(SkyFx.BLACK_HOLE));
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** Direccion fija en el cielo (azimut 40, elevacion segun efecto). */
    private static Vector3f skyDir(float azimuthDeg, float elevationDeg) {
        float az = (float) Math.toRadians(azimuthDeg);
        float el = (float) Math.toRadians(elevationDeg);
        return new Vector3f(
                Mth.cos(el) * Mth.cos(az) * SKY_DISTANCE,
                Mth.sin(el) * SKY_DISTANCE,
                Mth.cos(el) * Mth.sin(az) * SKY_DISTANCE);
    }

    /** El punto de luz que CRECE: nucleo blanco + halo naranja pulsante. */
    private static void renderApproach(Matrix4f matrix, float progress, float time) {
        Vector3f center = skyDir(40.0F, 58.0F);
        float size = 0.6F + progress * progress * 16.0F;
        float pulse = 1.0F + 0.08F * Mth.sin(time * 0.6F);
        billboard(matrix, center, size * 2.6F * pulse, 1.0F, 0.55F, 0.25F, 0.35F);
        billboard(matrix, center, size * 1.4F, 1.0F, 0.85F, 0.6F, 0.7F);
        billboard(matrix, center, size * 0.7F, 1.0F, 1.0F, 1.0F, 0.95F);
    }

    /** Estela que cruza el cielo hacia el horizonte. */
    private static void renderEntryStreak(Matrix4f matrix, float progress) {
        Vector3f from = skyDir(40.0F, 58.0F);
        Vector3f to = skyDir(52.0F, 8.0F);
        Vector3f head = new Vector3f(from).lerp(to, progress);
        // cabeza brillante
        billboard(matrix, head, 7.0F, 1.0F, 0.8F, 0.5F, 0.8F);
        billboard(matrix, head, 3.5F, 1.0F, 1.0F, 0.95F, 0.95F);
        // estela: puntos decrecientes hacia atras
        for (int i = 1; i <= 8; i++) {
            float t = progress - i * 0.045F;
            if (t < 0.0F) {
                break;
            }
            Vector3f p = new Vector3f(from).lerp(to, t);
            float fade = 1.0F - i / 9.0F;
            billboard(matrix, p, 4.0F * fade, 1.0F, 0.6F, 0.3F, 0.4F * fade);
        }
    }

    /** Agujero negro: disco negro + anillo de acrecion rotando. */
    private static void renderBlackHole(Matrix4f matrix, float time, float intensity) {
        Vector3f center = skyDir(-60.0F, 65.0F);
        float radius = 10.0F * intensity;
        float spin = time * 1.7F;

        // disco de acrecion: anillo de segmentos con gradiente (fan additivo)
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        Vector3f[] basis = billboardBasis(center);
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int segments = 40;
        for (int i = 0; i <= segments; i++) {
            float a = (float) (Math.PI * 2.0D * i / segments) + spin * 0.017F;
            float cos = Mth.cos(a);
            float sin = Mth.sin(a);
            // brillo desigual: el disco es mas brillante en un lado (doppler fake)
            float glow = 0.55F + 0.45F * Mth.cos(a - 0.8F);
            float inR = radius * 1.05F;
            float outR = radius * (1.75F + 0.06F * Mth.sin(a * 3.0F + spin * 0.05F));
            buffer.vertex(matrix,
                            center.x + basis[0].x * cos * inR + basis[1].x * sin * inR,
                            center.y + basis[0].y * cos * inR + basis[1].y * sin * inR,
                            center.z + basis[0].z * cos * inR + basis[1].z * sin * inR)
                    .color(1.0F, 0.75F * glow, 0.35F * glow, 0.85F * glow).endVertex();
            buffer.vertex(matrix,
                            center.x + basis[0].x * cos * outR + basis[1].x * sin * outR,
                            center.y + basis[0].y * cos * outR + basis[1].y * sin * outR,
                            center.z + basis[0].z * cos * outR + basis[1].z * sin * outR)
                    .color(0.9F, 0.35F * glow, 0.1F * glow, 0.0F).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        // el horizonte de sucesos: disco NEGRO opaco encima (blend normal)
        RenderSystem.defaultBlendFunc();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, center.x, center.y, center.z).color(0, 0, 0, 255).endVertex();
        for (int i = 0; i <= segments; i++) {
            float a = (float) (Math.PI * 2.0D * i / segments);
            float cos = Mth.cos(a) * radius;
            float sin = Mth.sin(a) * radius;
            buffer.vertex(matrix,
                            center.x + basis[0].x * cos + basis[1].x * sin,
                            center.y + basis[0].y * cos + basis[1].y * sin,
                            center.z + basis[0].z * cos + basis[1].z * sin)
                    .color(0, 0, 0, 255).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
    }

    private static Vector3f[] billboardBasis(Vector3f dir) {
        Vector3f normal = new Vector3f(dir).normalize();
        Vector3f up = Math.abs(normal.y) > 0.95F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(up).cross(normal).normalize();
        Vector3f realUp = new Vector3f(normal).cross(right).normalize();
        return new Vector3f[]{right, realUp};
    }

    private static void billboard(Matrix4f matrix, Vector3f center, float size,
                                  float r, float g, float b, float a) {
        Vector3f[] basis = billboardBasis(center);
        Vector3f right = new Vector3f(basis[0]).mul(size);
        Vector3f up = new Vector3f(basis[1]).mul(size);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, center.x - right.x - up.x, center.y - right.y - up.y,
                center.z - right.z - up.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, center.x + right.x - up.x, center.y + right.y - up.y,
                center.z + right.z - up.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, center.x + right.x + up.x, center.y + right.y + up.y,
                center.z + right.z + up.z).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, center.x - right.x + up.x, center.y - right.y + up.y,
                center.z - right.z + up.z).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private SkyRenderer() {
    }
}
