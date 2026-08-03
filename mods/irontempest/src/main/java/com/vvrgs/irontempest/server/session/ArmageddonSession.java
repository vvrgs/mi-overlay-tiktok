package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import com.vvrgs.irontempest.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Tier U (ultra, único). Exclusión GLOBAL: un solo armagedón por servidor.
 * Director de oleadas que REUTILIZA los managers de los demás tiers:
 * T+0 sirena+título global → T+40 lluvia de 40 cohetes → T+160/190/220
 * tres misiles crucero en abanico → T+300 tanque → T+520 ataque orbital →
 * T+760 lluvia de brasas → T+820 fin.
 */
public final class ArmageddonSession extends WarSession {

    private static final int T_ROCKETS = 40;
    private static final int[] T_MISSILES = {160, 190, 220};
    private static final int T_TANK = 300;
    private static final int T_ORBITAL = 520;
    private static final int T_EMBERS = 760;
    private static final int T_FIN = 820;

    private int missilesLaunched;

    ArmageddonSession(ServerLevel level, ServerPlayer target) {
        super(level, target, 'U', "armageddon");
        opening(target);
    }

    @Override
    protected int maxLifetime() {
        return 2400;
    }

    private void opening(ServerPlayer target) {
        // Broadcast total: título + sirena antiaérea para TODO el servidor.
        Component title = Component.translatable("irontempest.title.armageddon");
        Component subtitle = Component.translatable("irontempest.subtitle.armageddon", this.targetName);
        for (ServerPlayer p : this.level.getServer().getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            // La sirena debe oírla TODO el server, no solo un radio de 64 bloques.
            p.playNotifySound(ModSounds.ULTRA_SIREN.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
        }
        ModNetwork.fx(this.level, FxType.ARMAGEDDON_OPENING, target.position(), 2.0F);
        SessionManager.broadcastStarted(this.level, "armageddon", this.targetName);
    }

    @Override
    protected void tickInternal(@Nullable ServerPlayer target) {
        if (target == null) {
            return;
        }
        if (this.age == T_ROCKETS) {
            SessionManager.rocketRain(this.level, target, 7); // ~40 cohetes en cola
        }
        if (this.missilesLaunched < T_MISSILES.length && this.age == T_MISSILES[this.missilesLaunched]) {
            SessionManager.forceCruise(this.level, target, this.missilesLaunched);
            this.missilesLaunched++;
        }
        if (this.age == T_TANK) {
            SessionManager.forceTank(this.level, target);
        }
        if (this.age == T_ORBITAL) {
            SessionManager.forceOrbital(this.level, target);
        }
        if (this.age == T_EMBERS) {
            ModNetwork.fx(this.level, FxType.DEBRIS_RAIN, target.position(), 12.0F);
            // Tormenta final: ametralladora de tótems a 10 pops/s.
            com.vvrgs.irontempest.server.util.TotemShredder.shred(this.level, target, "armageddon", 25, 2);
        }
        if (this.age >= T_FIN) {
            end("complete");
        }
    }

    @Override
    protected void onEnd(String reason) {
        SessionManager.ultraFinished();
    }
}
