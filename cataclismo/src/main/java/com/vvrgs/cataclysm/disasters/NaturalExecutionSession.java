package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.core.TotemShredder;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.fx.SkyFx;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

/**
 * /ejecucion_natural — tier U. La ejecucion: el desastre ANCLA al jugador
 * (succion fisica hacia el punto, no puede huir) y la trituradora trabaja
 * a cadencia MAXIMA con contador en pantalla hasta agotar el presupuesto
 * (default 40 pops, configurable). Climax si sobrevive.
 */
public class NaturalExecutionSession extends DisasterSession {

    private static final int ANCHOR_IN = 60;
    private static final int DURATION = 20 * 90;

    private int initialBudget;
    private int maxPopped;
    private boolean shredStarted;

    public NaturalExecutionSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
        createBossBar(Component.translatable(kind().bossbarKey()),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20, true);
    }

    @Override
    public Disasters kind() {
        return Disasters.EJECUCION_NATURAL;
    }

    @Override
    protected int maxLifetimeTicks() {
        return DURATION + 60;
    }

    @Override
    protected void onTick(ServerPlayer target) {
        if (age == 1) {
            FxDirector.sound(level, anchor, ModSounds.KLAXON.get(), 4.0F, 1.0F);
            FxDirector.sky(server, SkyFx.RED_TINT, DURATION, 0.4F);
        }

        // ancla visible: columna estroboscopica en el punto
        if (age % 10 == 0) {
            FxDirector.fire(level, FxEvent.EXECUTION_ANCHOR, anchor, 1.0F);
        }
        if (age % 100 == 0 && age > 0) {
            FxDirector.sound(level, anchor, ModSounds.KLAXON.get(), 3.0F, 1.1F);
        }

        // NO PUEDE HUIR: succion fisica hacia el ancla, mas fuerte cuanto mas lejos
        Vec3 toAnchor = anchor.subtract(target.position());
        double dist = toAnchor.length();
        if (dist > 2.0D) {
            double pull = Math.min(0.55D, 0.06D + dist * 0.012D);
            Vec3 force = toAnchor.scale(pull / dist);
            target.setDeltaMovement(target.getDeltaMovement()
                    .add(force.x, Math.max(force.y * 0.5D, -0.1D), force.z));
            target.hurtMarked = true;
        }

        // arranque de la trituradora a cadencia maxima
        if (!shredStarted && age >= ANCHOR_IN) {
            shredStarted = true;
            initialBudget = CataclysmConfig.COMMON.executionPopBudget.get();
            TotemShredder.start(target, kind().commandName(), initialBudget, 2);
        }

        if (shredStarted) {
            // contador en pantalla: la bossbar RETIENE el maximo (no cae a 0
            // al acabar el shred)
            maxPopped = Math.max(maxPopped, TotemShredder.getPopped(targetId));
            if (bossBar != null && initialBudget > 0) {
                bossBar.setProgress(Mth.clamp((float) maxPopped / initialBudget, 0.0F, 1.0F));
                if (age % 10 == 0) {
                    bossBar.setName(Component.translatable(
                            "cataclysm.ejecucion.counter", maxPopped, initialBudget));
                }
            }
            // presupuesto agotado y sigue vivo => CLIMAX de superviviente
            if (!TotemShredder.isActive(targetId)) {
                if (target.isAlive()) {
                    FxDirector.fire(level, FxEvent.SHOCKWAVE_RING, target.position(), 1.0F, 20);
                    FxDirector.fire(level, FxEvent.IMPACT_FLASH, target.position(), 0.8F);
                    FxDirector.sound(level, target.position(), ModSounds.TOTEM_POP.get(), 2.0F, 0.6F);
                    TitleDirector.title(target,
                            Component.translatable("cataclysm.ejecucion.survived.title"),
                            Component.translatable("cataclysm.ejecucion.survived.subtitle"));
                }
                end("executed");
                return;
            }
        }

        if (age >= DURATION) {
            end("timeout_execution");
        }
    }

    @Override
    protected void onReanchor(ServerPlayer target, boolean dimensionChange) {
        // el ancla lo sigue: nueva posicion, mismo castigo
        FxDirector.fire(level, FxEvent.EXECUTION_ANCHOR, anchor, 1.5F);
        FxDirector.sound(level, anchor, ModSounds.KLAXON.get(), 3.0F, 0.9F);
    }

    @Override
    protected void onEnd(String reason) {
        FxDirector.skyClear(server, SkyFx.RED_TINT);
        TotemShredder.cancel(targetId, "session_end_" + reason);
    }
}
