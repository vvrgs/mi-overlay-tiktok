package net.minecraftforge.client.gui.overlay;

@FunctionalInterface
public interface IGuiOverlay {
    void render(ForgeGui gui, net.minecraft.client.gui.GuiGraphics graphics, float partialTick, int width, int height);
}
