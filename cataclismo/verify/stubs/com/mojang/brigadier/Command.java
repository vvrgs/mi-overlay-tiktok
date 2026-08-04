package com.mojang.brigadier;

@FunctionalInterface
public interface Command<S> {
    int run(com.mojang.brigadier.context.CommandContext<S> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException;
}
