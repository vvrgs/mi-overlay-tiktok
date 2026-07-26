package net.minecraftforge.common;

import net.minecraftforge.fml.config.IConfigSpec;

public class ForgeConfigSpec implements IConfigSpec<ForgeConfigSpec> {
    public static class Builder {
        public Builder comment(String comment) { return this; }
        public Builder comment(String... comment) { return this; }
        public Builder push(String path) { return this; }
        public Builder pop() { return this; }
        public BooleanValue define(String path, boolean defaultValue) { throw new UnsupportedOperationException(); }
        public DoubleValue defineInRange(String path, double defaultValue, double min, double max) { throw new UnsupportedOperationException(); }
        public IntValue defineInRange(String path, int defaultValue, int min, int max) { throw new UnsupportedOperationException(); }
        public ForgeConfigSpec build() { throw new UnsupportedOperationException(); }
    }

    public static class ConfigValue<T> {
        public T get() { throw new UnsupportedOperationException(); }
    }

    public static class BooleanValue extends ConfigValue<Boolean> {
    }

    public static class DoubleValue extends ConfigValue<Double> {
    }

    public static class IntValue extends ConfigValue<Integer> {
    }
}
