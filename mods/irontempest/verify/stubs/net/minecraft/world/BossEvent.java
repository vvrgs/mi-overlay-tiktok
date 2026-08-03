package net.minecraft.world;

import java.util.UUID;
import net.minecraft.network.chat.Component;

public abstract class BossEvent {
    protected Component name;
    protected float progress;
    protected BossBarColor color;
    protected BossBarOverlay overlay;

    public BossEvent(UUID id, Component name, BossBarColor color, BossBarOverlay overlay) {}

    public UUID getId() { throw new UnsupportedOperationException(); }
    public Component getName() { throw new UnsupportedOperationException(); }
    public void setName(Component name) {}
    public float getProgress() { return 0.0F; }
    public void setProgress(float progress) {}
    public BossBarColor getColor() { throw new UnsupportedOperationException(); }
    public void setColor(BossBarColor color) {}
    public BossBarOverlay getOverlay() { throw new UnsupportedOperationException(); }
    public void setOverlay(BossBarOverlay overlay) {}
    public boolean shouldDarkenScreen() { return false; }
    public BossEvent setDarkenScreen(boolean darken) { return this; }
    public boolean shouldPlayBossMusic() { return false; }
    public BossEvent setPlayBossMusic(boolean music) { return this; }
    public BossEvent setCreateWorldFog(boolean fog) { return this; }
    public boolean shouldCreateWorldFog() { return false; }

    public enum BossBarColor { PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE }

    public enum BossBarOverlay { PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20 }
}
