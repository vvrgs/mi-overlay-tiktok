package net.minecraftforge.event;

public class TickEvent extends net.minecraftforge.eventbus.api.Event {
    public enum Phase { START, END }
    public final Phase phase;
    protected TickEvent(Phase phase) { this.phase = phase; }
    public static class ServerTickEvent extends TickEvent {
        public ServerTickEvent(Phase phase) { super(phase); }
    }
    public static class ClientTickEvent extends TickEvent {
        public ClientTickEvent(Phase phase) { super(phase); }
    }
}
