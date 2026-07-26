package net.minecraft.world.phys;

import net.minecraft.world.entity.Entity;

public class EntityHitResult extends HitResult {
    public EntityHitResult(Entity entity) {}

    public Entity getEntity() { throw new UnsupportedOperationException(); }

    @Override
    public Type getType() { return Type.ENTITY; }
}
