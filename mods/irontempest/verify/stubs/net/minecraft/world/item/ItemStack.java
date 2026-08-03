package net.minecraft.world.item;

public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack();

    public boolean is(Item item) { return false; }
    public int getCount() { return 0; }
    public boolean isEmpty() { return true; }
}
