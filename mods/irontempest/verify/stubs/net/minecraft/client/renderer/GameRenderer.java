package net.minecraft.client.renderer;

import net.minecraft.client.Camera;
import org.jetbrains.annotations.Nullable;

public class GameRenderer {
    public Camera getMainCamera() { throw new UnsupportedOperationException(); }

    @Nullable
    public static ShaderInstance getPositionColorTexShader() { return null; }
}
