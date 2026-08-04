package net.minecraft.client.particle;

public interface SpriteSet {
    net.minecraft.client.renderer.texture.TextureAtlasSprite get(int age, int lifetime);
    net.minecraft.client.renderer.texture.TextureAtlasSprite get(net.minecraft.util.RandomSource random);
}
