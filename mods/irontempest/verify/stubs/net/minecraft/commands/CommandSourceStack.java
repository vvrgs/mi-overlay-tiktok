package net.minecraft.commands;

import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class CommandSourceStack {
    public boolean hasPermission(int level) { return false; }
    public void sendFailure(Component message) {}
    public void sendSuccess(Supplier<Component> message, boolean allowLogging) {}
    public MinecraftServer getServer() { throw new UnsupportedOperationException(); }
}
