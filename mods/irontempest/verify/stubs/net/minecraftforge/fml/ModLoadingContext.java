package net.minecraftforge.fml;

import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

public class ModLoadingContext {
    public static ModLoadingContext get() { throw new UnsupportedOperationException(); }
    public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec) {}
}
