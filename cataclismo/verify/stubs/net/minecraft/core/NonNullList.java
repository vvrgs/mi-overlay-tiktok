package net.minecraft.core;

public class NonNullList<E> extends java.util.AbstractList<E> {
    @Override public E get(int index) { throw new UnsupportedOperationException(); }
    @Override public E set(int index, E element) { throw new UnsupportedOperationException(); }
    @Override public int size() { throw new UnsupportedOperationException(); }
}
