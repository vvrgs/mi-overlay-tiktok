package net.minecraft.server.level;

public class ServerPlayer extends net.minecraft.world.entity.player.Player {
    public final net.minecraft.server.MinecraftServer server = null;
    public final net.minecraft.server.network.ServerGamePacketListenerImpl connection = new net.minecraft.server.network.ServerGamePacketListenerImpl();
    public ServerPlayer(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.player.Player> type, net.minecraft.world.level.Level level) { super(type, level); }
    @Override protected void defineSynchedData() {}
    public ServerLevel serverLevel() { throw new UnsupportedOperationException(); }
    public net.minecraft.world.entity.player.Inventory getInventory() { throw new UnsupportedOperationException(); }
    public void playNotifySound(net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source, float volume, float pitch) {}
}
