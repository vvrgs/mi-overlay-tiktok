package net.minecraftforge.client.event;

import java.util.function.Consumer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.eventbus.api.Event;

public class RegisterShadersEvent extends Event {
    public ResourceProvider getResourceProvider() { throw new UnsupportedOperationException(); }
    public void registerShader(ShaderInstance shader, Consumer<ShaderInstance> onLoaded) {}
}
