package com.vvrgs.irontempest.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.server.session.SessionManager;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Contrato TikTok: comandos de consola simples que StreamToEarn/TikFinity
 * ejecutan vía ServerTap. Todos aceptan [jugador] opcional; sin argumento
 * (consola) atacan al primer jugador online — regla del skill.
 *
 * /irontempest rocketrain [jugador] [salvas]
 * /irontempest cruisemissile [jugador]
 * /irontempest tankblitz [jugador]
 * /irontempest orbitalstrike [jugador]
 * /irontempest armageddon [jugador]
 * /irontempest stopall
 */
@Mod.EventBusSubscriber(modid = IronTempest.MODID)
public final class WarCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("irontempest")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("rocketrain")
                        .executes(ctx -> rocketRain(ctx, null, 1))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> rocketRain(ctx, EntityArgument.getPlayer(ctx, "target"), 1))
                                .then(Commands.argument("salvos", IntegerArgumentType.integer(1, 50))
                                        .executes(ctx -> rocketRain(ctx, EntityArgument.getPlayer(ctx, "target"),
                                                IntegerArgumentType.getInteger(ctx, "salvos"))))))
                .then(Commands.literal("cruisemissile")
                        .executes(ctx -> cruiseMissile(ctx, null))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> cruiseMissile(ctx, EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("tankblitz")
                        .executes(ctx -> tankBlitz(ctx, null))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> tankBlitz(ctx, EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("orbitalstrike")
                        .executes(ctx -> orbitalStrike(ctx, null))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> orbitalStrike(ctx, EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("armageddon")
                        .executes(ctx -> armageddon(ctx, null))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> armageddon(ctx, EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("stopall")
                        .executes(WarCommands::stopAll)));
    }

    /** Consola sin argumento → primer jugador online real (regla del skill). */
    @Nullable
    private static ServerPlayer resolveTarget(CommandContext<CommandSourceStack> ctx,
                                              @Nullable ServerPlayer explicit) {
        if (explicit != null) {
            return explicit;
        }
        List<ServerPlayer> players = ctx.getSource().getServer().getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            if (p.isAlive() && !p.isSpectator()) {
                return p;
            }
        }
        ctx.getSource().sendFailure(Component.translatable("irontempest.msg.no_players"));
        return null;
    }

    private static int rocketRain(CommandContext<CommandSourceStack> ctx,
                                  @Nullable ServerPlayer explicit, int salvos) {
        ServerPlayer target = resolveTarget(ctx, explicit);
        if (target == null) {
            return 0;
        }
        SessionManager.rocketRain(target.serverLevel(), target, salvos);
        feedbackStarted(ctx, "rocketrain", target);
        return 1;
    }

    private static int cruiseMissile(CommandContext<CommandSourceStack> ctx,
                                     @Nullable ServerPlayer explicit) {
        ServerPlayer target = resolveTarget(ctx, explicit);
        if (target == null) {
            return 0;
        }
        int queued = SessionManager.cruiseMissile(target.serverLevel(), target);
        feedback(ctx, "cruisemissile", target, queued);
        return 1;
    }

    private static int tankBlitz(CommandContext<CommandSourceStack> ctx,
                                 @Nullable ServerPlayer explicit) {
        ServerPlayer target = resolveTarget(ctx, explicit);
        if (target == null) {
            return 0;
        }
        int queued = SessionManager.tankBlitz(target.serverLevel(), target);
        feedback(ctx, "tankblitz", target, queued);
        return 1;
    }

    private static int orbitalStrike(CommandContext<CommandSourceStack> ctx,
                                     @Nullable ServerPlayer explicit) {
        ServerPlayer target = resolveTarget(ctx, explicit);
        if (target == null) {
            return 0;
        }
        int queued = SessionManager.orbitalStrike(target.serverLevel(), target);
        feedback(ctx, "orbitalstrike", target, queued);
        return 1;
    }

    private static int armageddon(CommandContext<CommandSourceStack> ctx,
                                  @Nullable ServerPlayer explicit) {
        ServerPlayer target = resolveTarget(ctx, explicit);
        if (target == null) {
            return 0;
        }
        boolean started = SessionManager.armageddon(target.serverLevel(), target);
        if (!started) {
            ctx.getSource().sendFailure(Component.translatable("irontempest.msg.ultra_busy"));
            return 0;
        }
        feedbackStarted(ctx, "armageddon", target);
        return 1;
    }

    private static int stopAll(CommandContext<CommandSourceStack> ctx) {
        int n = SessionManager.stopAll();
        ctx.getSource().sendSuccess(
                () -> Component.translatable("irontempest.msg.stopped_all", n), true);
        return n;
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String attackKey,
                                 ServerPlayer target, int queued) {
        if (queued > 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("irontempest.msg.attack_queued",
                    Component.translatable("irontempest.attack." + attackKey),
                    target.getGameProfile().getName(), queued), true);
        } else {
            feedbackStarted(ctx, attackKey, target);
        }
    }

    private static void feedbackStarted(CommandContext<CommandSourceStack> ctx, String attackKey,
                                        ServerPlayer target) {
        ctx.getSource().sendSuccess(() -> Component.translatable("irontempest.msg.attack_started",
                Component.translatable("irontempest.attack." + attackKey),
                target.getGameProfile().getName()), true);
    }

    private WarCommands() {}
}
