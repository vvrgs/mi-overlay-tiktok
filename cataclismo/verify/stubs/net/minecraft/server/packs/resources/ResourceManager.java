package net.minecraft.server.packs.resources;

public interface ResourceManager extends ResourceProvider {
    java.util.Optional<Resource> getResource(net.minecraft.resources.ResourceLocation location);
}
