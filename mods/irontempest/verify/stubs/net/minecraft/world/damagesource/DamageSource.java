package net.minecraft.world.damagesource;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public class DamageSource {
    public DamageSource(Holder<DamageType> type) {}
    public DamageSource(Holder<DamageType> type, @Nullable Entity directEntity) {}
    public DamageSource(Holder<DamageType> type, @Nullable Entity directEntity, @Nullable Entity causingEntity) {}
}
