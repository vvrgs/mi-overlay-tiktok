package net.minecraft.core;

import java.util.AbstractList;

public class NonNullList<E> extends AbstractList<E> {
    @Override
    public E get(int index) { throw new UnsupportedOperationException(); }
    @Override
    public E set(int index, E element) { throw new UnsupportedOperationException(); }
    @Override
    public int size() { return 0; }
}
