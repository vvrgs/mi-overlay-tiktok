package net.minecraft.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class Commands {
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) { throw new UnsupportedOperationException(); }
    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) { throw new UnsupportedOperationException(); }
}
