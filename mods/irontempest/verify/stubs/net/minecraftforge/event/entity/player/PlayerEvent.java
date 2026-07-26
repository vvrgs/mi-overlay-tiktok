package net.minecraftforge.event.entity.player;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

public class PlayerEvent extends Event {
    public Player getEntity() { throw new UnsupportedOperationException(); }

    public static class PlayerLoggedInEvent extends PlayerEvent {
    }

    public static class PlayerLoggedOutEvent extends PlayerEvent {
    }
}
