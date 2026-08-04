package com.mojang.brigadier.builder;

public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public T requires(java.util.function.Predicate<S> predicate) { throw new UnsupportedOperationException(); }
    public T executes(com.mojang.brigadier.Command<S> command) { throw new UnsupportedOperationException(); }
    public T then(ArgumentBuilder<S, ?> child) { throw new UnsupportedOperationException(); }
}
