package com.mojang.brigadier.builder;

import com.mojang.brigadier.Command;
import java.util.function.Predicate;

public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public T then(ArgumentBuilder<S, ?> argument) { throw new UnsupportedOperationException(); }
    public T executes(Command<S> command) { throw new UnsupportedOperationException(); }
    public T requires(Predicate<S> requirement) { throw new UnsupportedOperationException(); }
}
