package net.minecraft.commands;

public class CommandSourceStack {
    public boolean hasPermission(int level) { throw new UnsupportedOperationException(); }
    public net.minecraft.world.entity.Entity getEntity() { throw new UnsupportedOperationException(); }
    public net.minecraft.server.MinecraftServer getServer() { throw new UnsupportedOperationException(); }
    public void sendSuccess(java.util.function.Supplier<net.minecraft.network.chat.Component> message, boolean allowLogging) {}
    public void sendFailure(net.minecraft.network.chat.Component message) {}
}
