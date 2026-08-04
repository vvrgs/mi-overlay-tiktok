package net.minecraft.client.particle;

public abstract class TextureSheetParticle extends SingleQuadParticle {
    protected TextureSheetParticle(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z) { super(level, x, y, z); }
    protected TextureSheetParticle(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z, double xd, double yd, double zd) { super(level, x, y, z, xd, yd, zd); }
    protected void setSprite(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {}
    public void setSpriteFromAge(SpriteSet sprites) {}
    public void pickSprite(SpriteSet sprites) {}
}
