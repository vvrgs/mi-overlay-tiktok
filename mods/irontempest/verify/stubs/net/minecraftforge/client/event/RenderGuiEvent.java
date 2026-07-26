package net.minecraftforge.client.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.eventbus.api.Event;

public abstract class RenderGuiEvent extends Event {
    public GuiGraphics getGuiGraphics() { throw new UnsupportedOperationException(); }
    public float getPartialTick() { return 0.0F; }

    public static class Pre extends RenderGuiEvent {
    }

    public static class Post extends RenderGuiEvent {
    }
}
