package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;

public abstract class TextureSheetParticle extends SingleQuadParticle {
    protected TextureSheetParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    protected TextureSheetParticle(ClientLevel level, double x, double y, double z,
                                   double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    public void pickSprite(SpriteSet sprites) {}
    public void setSpriteFromAge(SpriteSet sprites) {}

    @Override
    protected float getU0() { return 0.0F; }
    @Override
    protected float getU1() { return 0.0F; }
    @Override
    protected float getV0() { return 0.0F; }
    @Override
    protected float getV1() { return 0.0F; }
}
