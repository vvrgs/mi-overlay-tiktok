package net.minecraft.world.entity.projectile;

public abstract class Projectile extends net.minecraft.world.entity.Entity {
    protected Projectile(net.minecraft.world.entity.EntityType<? extends Projectile> type, net.minecraft.world.level.Level level) { super(type, level); }
}
