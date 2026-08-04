package net.minecraftforge.client.event;

public class ViewportEvent extends net.minecraftforge.eventbus.api.Event {
    public double getPartialTick() { throw new UnsupportedOperationException(); }
    public static class ComputeCameraAngles extends ViewportEvent {
        public float getYaw() { throw new UnsupportedOperationException(); }
        public void setYaw(float yaw) {}
        public float getPitch() { throw new UnsupportedOperationException(); }
        public void setPitch(float pitch) {}
        public float getRoll() { throw new UnsupportedOperationException(); }
        public void setRoll(float roll) {}
    }
    public static class ComputeFogColor extends ViewportEvent {
        public float getRed() { throw new UnsupportedOperationException(); }
        public void setRed(float red) {}
        public float getGreen() { throw new UnsupportedOperationException(); }
        public void setGreen(float green) {}
        public float getBlue() { throw new UnsupportedOperationException(); }
        public void setBlue(float blue) {}
    }
}
