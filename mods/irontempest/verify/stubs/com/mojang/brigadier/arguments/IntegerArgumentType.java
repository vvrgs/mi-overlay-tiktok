package com.mojang.brigadier.arguments;

import com.mojang.brigadier.context.CommandContext;

public class IntegerArgumentType implements ArgumentType<Integer> {
    public static IntegerArgumentType integer() { throw new UnsupportedOperationException(); }
    public static IntegerArgumentType integer(int min) { throw new UnsupportedOperationException(); }
    public static IntegerArgumentType integer(int min, int max) { throw new UnsupportedOperationException(); }
    public static int getInteger(CommandContext<?> context, String name) { throw new UnsupportedOperationException(); }
}
