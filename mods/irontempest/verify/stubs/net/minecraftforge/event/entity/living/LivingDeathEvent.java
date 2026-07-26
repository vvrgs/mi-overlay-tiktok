package net.minecraftforge.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

public class LivingDeathEvent extends Event {
    public LivingEntity getEntity() { throw new UnsupportedOperationException(); }
}
