package net.minecraftforge.client.event;

import net.minecraftforge.eventbus.api.Event;

public abstract class ViewportEvent extends Event {
    public double getPartialTick() { return 0.0D; }

    public static class ComputeCameraAngles extends ViewportEvent {
        public float getYaw() { return 0.0F; }
        public void setYaw(float yaw) {}
        public float getPitch() { return 0.0F; }
        public void setPitch(float pitch) {}
        public float getRoll() { return 0.0F; }
        public void setRoll(float roll) {}
    }
}
