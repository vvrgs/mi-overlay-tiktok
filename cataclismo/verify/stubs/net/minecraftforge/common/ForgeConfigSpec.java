package net.minecraftforge.common;

public class ForgeConfigSpec {
    public static class Builder {
        public Builder comment(String... comment) { return this; }
        public Builder push(String path) { return this; }
        public Builder pop() { return this; }
        public BooleanValue define(String path, boolean defaultValue) { throw new UnsupportedOperationException(); }
        public DoubleValue defineInRange(String path, double defaultValue, double min, double max) { throw new UnsupportedOperationException(); }
        public IntValue defineInRange(String path, int defaultValue, int min, int max) { throw new UnsupportedOperationException(); }
        public <T> org.apache.commons.lang3.tuple.Pair<T, ForgeConfigSpec> configure(java.util.function.Function<Builder, T> factory) { throw new UnsupportedOperationException(); }
    }
    public abstract static class ConfigValue<T> {
        public T get() { throw new UnsupportedOperationException(); }
    }
    public static class BooleanValue extends ConfigValue<Boolean> {}
    public static class DoubleValue extends ConfigValue<Double> {}
    public static class IntValue extends ConfigValue<Integer> {}
}
