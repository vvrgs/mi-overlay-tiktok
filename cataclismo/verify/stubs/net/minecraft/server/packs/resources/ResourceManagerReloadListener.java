package net.minecraft.server.packs.resources;

public interface ResourceManagerReloadListener extends PreparableReloadListener {
    void onResourceManagerReload(ResourceManager manager);
}
