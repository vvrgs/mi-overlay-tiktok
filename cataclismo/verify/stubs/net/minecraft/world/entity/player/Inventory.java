package net.minecraft.world.entity.player;

public class Inventory implements net.minecraft.world.Container {
    public final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items = new net.minecraft.core.NonNullList<>();
    public int getContainerSize() { throw new UnsupportedOperationException(); }
    public net.minecraft.world.item.ItemStack getItem(int slot) { throw new UnsupportedOperationException(); }
}
