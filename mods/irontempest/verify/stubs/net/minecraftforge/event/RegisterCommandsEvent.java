package net.minecraftforge.event;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.eventbus.api.Event;

public class RegisterCommandsEvent extends Event {
    public CommandDispatcher<CommandSourceStack> getDispatcher() { throw new UnsupportedOperationException(); }
}
