package com.mojang.blaze3d.pipeline;

public abstract class RenderTarget {
    public int width;
    public int height;
    public int viewWidth;
    public int viewHeight;
    public int frameBufferId;

    public RenderTarget(boolean useDepth) {}

    public void resize(int width, int height, boolean clearError) { throw new UnsupportedOperationException(); }
    public void clear(boolean clearError) { throw new UnsupportedOperationException(); }
    public void bindWrite(boolean setViewport) { throw new UnsupportedOperationException(); }
    public void unbindWrite() { throw new UnsupportedOperationException(); }
    public void setClearColor(float red, float green, float blue, float alpha) {}
    public int getColorTextureId() { return 0; }
}
