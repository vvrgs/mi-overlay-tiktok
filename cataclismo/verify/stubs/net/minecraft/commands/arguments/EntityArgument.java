package net.minecraft.commands.arguments;

public class EntityArgument implements com.mojang.brigadier.arguments.ArgumentType<Object> {
    public static EntityArgument player() { throw new UnsupportedOperationException(); }
    public static net.minecraft.server.level.ServerPlayer getPlayer(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException { throw new UnsupportedOperationException(); }
}
