package net.minecraftforge.event.level;

public class LevelEvent extends net.minecraftforge.eventbus.api.Event {
    public net.minecraft.world.level.LevelAccessor getLevel() { throw new UnsupportedOperationException(); }
    public static class Unload extends LevelEvent {}
}
