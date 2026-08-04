package net.minecraft.world.entity.player;

public abstract class Player extends net.minecraft.world.entity.LivingEntity {
    public final net.minecraft.world.inventory.InventoryMenu inventoryMenu = new net.minecraft.world.inventory.InventoryMenu();
    protected Player(net.minecraft.world.entity.EntityType<? extends Player> type, net.minecraft.world.level.Level level) { super(type, level); }
    public Inventory getInventory() { throw new UnsupportedOperationException(); }
    public com.mojang.authlib.GameProfile getGameProfile() { throw new UnsupportedOperationException(); }
    public boolean isCreative() { throw new UnsupportedOperationException(); }
    public boolean isSpectator() { throw new UnsupportedOperationException(); }
    public void displayClientMessage(net.minecraft.network.chat.Component message, boolean actionBar) {}
}
