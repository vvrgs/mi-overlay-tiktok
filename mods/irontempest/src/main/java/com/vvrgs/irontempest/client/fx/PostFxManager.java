package com.vvrgs.irontempest.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.config.WarConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;

/**
 * Mini-pipeline de post-proceso propio: distorsión radial de onda de choque.
 * Un solo pase EffectInstance (shaders/program/shockwave) sobre el framebuffer
 * principal. Se desactiva solo si hay shaderpack (Oculus/Iris) para no pelear
 * con su pipeline.
 */
public final class PostFxManager {

    private static final int WAVE_LIFE = 22;
    private static final int MAX_WAVES = 4;

    private static final class Wave {
        final Vec3 pos;
        final float strength;
        int age;

        Wave(Vec3 pos, float strength) {
            this.pos = pos;
            this.strength = strength;
        }
    }

    private static final List<Wave> WAVES = new ArrayList<>();
    private static RenderTarget temp;
    private static EffectInstance effect;
    private static boolean loadFailed;
    private static Boolean shaderpackPresent;

    public static void shockwave(Vec3 pos, float strength) {
        if (!enabled()) {
            return;
        }
        if (WAVES.size() >= MAX_WAVES) {
            WAVES.remove(0);
        }
        WAVES.add(new Wave(pos, Mth.clamp(strength, 0.0F, 2.0F)));
    }

    public static void tick() {
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            if (++it.next().age > WAVE_LIFE) {
                it.remove();
            }
        }
    }

    private static boolean enabled() {
        if (!WarConfig.POST_SHADER.get() || loadFailed) {
            return false;
        }
        if (shaderpackPresent == null) {
            shaderpackPresent = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
            if (shaderpackPresent) {
                IronTempest.LOGGER.info("[irontempest] Oculus/Iris detectado: post-shader de onda desactivado (compat)");
            }
        }
        return !shaderpackPresent;
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || WAVES.isEmpty() || !enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick();

        // Onda dominante: la más fuerte en pantalla en este instante.
        Wave best = null;
        float bestPower = 0.0F;
        for (Wave w : WAVES) {
            float life = (w.age + pt) / WAVE_LIFE;
            float power = w.strength * (1.0F - life);
            if (power > bestPower) {
                bestPower = power;
                best = w;
            }
        }
        if (best == null) {
            return;
        }

        // Proyección del centro de la onda a UV de pantalla.
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f mvp = new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        Vector4f clip = mvp.transform(new Vector4f(
                (float) (best.pos.x - cam.x), (float) (best.pos.y - cam.y), (float) (best.pos.z - cam.z), 1.0F));
        if (clip.w() <= 0.05F) {
            return; // detrás de la cámara
        }
        float u = clip.x() / clip.w() * 0.5F + 0.5F;
        float v = clip.y() / clip.w() * 0.5F + 0.5F;
        if (u < -0.4F || u > 1.4F || v < -0.4F || v > 1.4F) {
            return;
        }

        if (!ensureResources(mc)) {
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        if (temp.width != main.width || temp.height != main.height) {
            temp.resize(main.width, main.height, Minecraft.ON_OSX);
        }

        float life = (best.age + pt) / WAVE_LIFE;
        float radius = easeOutCubic(life) * 1.25F;
        float strength = bestPower;

        effect.setSampler("DiffuseSampler", main::getColorTextureId);
        effect.safeGetUniform("Center").set(u, v);
        effect.safeGetUniform("Radius").set(radius);
        effect.safeGetUniform("Strength").set(strength);
        effect.safeGetUniform("Aspect").set((float) main.width / (float) main.height);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.resetTextureMatrix();

        temp.clear(Minecraft.ON_OSX);
        temp.bindWrite(false);
        effect.apply();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(-1.0D, -1.0D, 0.0D).endVertex();
        builder.vertex(1.0D, -1.0D, 0.0D).endVertex();
        builder.vertex(1.0D, 1.0D, 0.0D).endVertex();
        builder.vertex(-1.0D, 1.0D, 0.0D).endVertex();
        BufferUploader.draw(builder.end());
        effect.clear();

        // temp → main (blit directo de color).
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, temp.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, temp.width, temp.height,
                0, 0, main.width, main.height, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);

        main.bindWrite(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static boolean ensureResources(Minecraft mc) {
        if (loadFailed) {
            return false;
        }
        if (effect == null) {
            try {
                effect = new EffectInstance(mc.getResourceManager(), IronTempest.MODID + ":shockwave");
            } catch (IOException | RuntimeException e) {
                loadFailed = true;
                IronTempest.LOGGER.error("[irontempest] no se pudo cargar el post-shader shockwave; desactivado", e);
                return false;
            }
        }
        if (temp == null) {
            RenderTarget main = mc.getMainRenderTarget();
            temp = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            temp.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
        return true;
    }

    private static float easeOutCubic(float x) {
        float inv = 1.0F - x;
        return 1.0F - inv * inv * inv;
    }

    private PostFxManager() {}
}
