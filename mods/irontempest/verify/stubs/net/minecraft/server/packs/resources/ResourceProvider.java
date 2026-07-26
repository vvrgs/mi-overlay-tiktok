package net.minecraft.server.packs.resources;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public interface ResourceProvider {
    Optional<Resource> getResource(ResourceLocation location);
}
