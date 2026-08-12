package com.vvrgs.cataclysm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.core.DisasterManager;
import com.vvrgs.cataclysm.core.Disasters;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Contrato TikTok -> consola: UN comando raiz `/disaster` con un subcomando
 * por desastre, en espanol, sin mayusculas ni tildes en el literal:
 *   /disaster meteoros [jugador]
 *   /disaster tornado  [jugador]
 *   /disaster stopall
 *
 * FALLBACK DE CONSOLA (el contrato principal, no un extra): los regalos de
 * TikTok (Stream to Earn / TikFinity / ServerTap) ejecutan comandos de
 * CONSOLA sin argumento — si la source no es un jugador y no se pasa
 * argumento, se apunta al PRIMER jugador online.
 */
@Mod.EventBusSubscriber(modid = Cataclysm.MODID)
public final class CataclysmCommands {

    /** Raiz de todos los comandos del mod. */
    public static final String ROOT = "disaster";

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT)
                .requires(src -> src.hasPermission(2));

        for (Disasters kind : Disasters.values()) {
            root.then(Commands.literal(kind.commandName())
                    .executes(ctx -> execute(ctx.getSource(), kind, null))
                    .then(Commands.argument("jugador", EntityArgument.player())
                            .executes(ctx -> execute(ctx.getSource(), kind,
                                    EntityArgument.getPlayer(ctx, "jugador")))));
        }

        root.then(Commands.literal("stopall")
                .executes(ctx -> stopAll(ctx.getSource())));

        dispatcher.register(root);
    }

    private static int execute(CommandSourceStack source, Disasters kind, @Nullable ServerPlayer explicit) {
        ServerPlayer target = explicit;
        if (target == null) {
            if (source.getEntity() instanceof ServerPlayer self) {
                target = self;
            } else {
                List<ServerPlayer> online = source.getServer().getPlayerList().getPlayers();
                if (online.isEmpty()) {
                    source.sendFailure(Component.translatable("cataclysm.command.no_players"));
                    return 0;
                }
                target = online.get(0);
            }
        }
        final ServerPlayer resolved = target;
        DisasterManager.SubmitResult result = DisasterManager.submit(source.getServer(), resolved, kind);
        switch (result) {
            case STARTED -> source.sendSuccess(() -> Component.translatable(
                    "cataclysm.command.started", kind.commandName(), resolved.getDisplayName()), true);
            case QUEUED -> source.sendSuccess(() -> Component.translatable(
                    "cataclysm.command.queued", kind.commandName(), resolved.getDisplayName()), true);
            case QUEUE_FULL -> source.sendFailure(Component.translatable(
                    "cataclysm.command.queue_full", kind.commandName())); // JAMAS fallar en silencio
        }
        return result == DisasterManager.SubmitResult.QUEUE_FULL ? 0 : 1;
    }

    private static int stopAll(CommandSourceStack source) {
        int stopped = DisasterManager.stopAll(source.getServer(), "stopall");
        source.sendSuccess(() -> Component.translatable("cataclysm.command.stopall", stopped), true);
        return stopped;
    }

    private CataclysmCommands() {
    }
}
