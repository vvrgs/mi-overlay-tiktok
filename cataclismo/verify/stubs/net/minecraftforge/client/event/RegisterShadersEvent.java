package net.minecraftforge.client.event;

public class RegisterShadersEvent extends net.minecraftforge.eventbus.api.Event {
    public net.minecraft.server.packs.resources.ResourceProvider getResourceProvider() { throw new UnsupportedOperationException(); }
    public void registerShader(net.minecraft.client.renderer.ShaderInstance shader, java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance> onLoaded) {}
}
