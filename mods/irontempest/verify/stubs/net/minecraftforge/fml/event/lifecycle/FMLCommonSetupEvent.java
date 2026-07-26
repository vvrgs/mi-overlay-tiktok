package net.minecraftforge.fml.event.lifecycle;

import java.util.concurrent.CompletableFuture;
import net.minecraftforge.eventbus.api.Event;

public class FMLCommonSetupEvent extends Event {
    public CompletableFuture<Void> enqueueWork(Runnable work) { throw new UnsupportedOperationException(); }
}
