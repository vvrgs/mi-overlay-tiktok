package com.mojang.blaze3d.systems;

public final class RenderSystem {
    public static void enableBlend() {}
    public static void disableBlend() {}
    public static void defaultBlendFunc() {}
    public static void blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor src, com.mojang.blaze3d.platform.GlStateManager.DestFactor dst) {}
    public static void enableDepthTest() {}
    public static void disableDepthTest() {}
    public static void enableCull() {}
    public static void disableCull() {}
    public static void setShader(java.util.function.Supplier<net.minecraft.client.renderer.ShaderInstance> shader) {}
}
