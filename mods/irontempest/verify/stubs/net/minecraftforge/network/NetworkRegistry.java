package net.minecraftforge.network;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkRegistry {
    public static SimpleChannel newSimpleChannel(ResourceLocation name, Supplier<String> networkProtocolVersion,
                                                 Predicate<String> clientAcceptedVersions,
                                                 Predicate<String> serverAcceptedVersions) {
        throw new UnsupportedOperationException();
    }
}
