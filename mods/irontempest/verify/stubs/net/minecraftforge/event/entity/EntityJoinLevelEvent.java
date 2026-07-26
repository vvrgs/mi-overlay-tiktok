package net.minecraftforge.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

public class EntityJoinLevelEvent extends Event {
    public Entity getEntity() { throw new UnsupportedOperationException(); }
    public Level getLevel() { throw new UnsupportedOperationException(); }
}
