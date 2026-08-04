package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.SpamQueueSession;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.core.TotemShredder;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * /rayo — tier S. Rayo dirigido REAL (no el LightningBolt vanilla).
 *
 * Coreografia por unidad:
 *   T+0  ionizacion visible: motas electricas ascendentes alrededor del
 *        jugador + zumbido + actionbar de aviso
 *   T+8  descarga: rama fractal propia desde el cielo al jugador (a su
 *        posicion ACTUAL — dirigido), flash, dano y 2 pops de totem
 *   T+8+d trueno con retardo por distancia (lo aplica la receta del cliente)
 */
public class LightningQueueSession extends DisasterSession implements SpamQueueSession {

    private static final int DRAIN_INTERVAL = 10;
    private static final int IONIZATION_TICKS = 8;
    private static final int IDLE_GRACE = 40;

    private int pending;
    private int drainTimer;
    private int idleTicks;
    /** tick de descarga del rayo en curso; -1 = ninguno cargandose */
    private int dischargeTick = -1;

    public LightningQueueSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
    }

    @Override
    public Disasters kind() {
        return Disasters.RAYO;
    }

    @Override
    protected int maxLifetimeTicks() {
        return 20 * 600;
    }

    @Override
    public boolean enqueueSpam(int amount) {
        int cap = CataclysmConfig.COMMON.maxTierSQueue.get();
        if (pending + amount > cap) {
            return false;
        }
        pending += amount;
        idleTicks = 0;
        return true;
    }

    @Override
    public int pendingSpam() {
        return pending;
    }

    @Override
    protected void onTick(ServerPlayer target) {
        ServerLevel level = target.serverLevel();

        if (dischargeTick < 0 && pending > 0 && ++drainTimer >= DRAIN_INTERVAL) {
            drainTimer = 0;
            pending--;
            // fase de ionizacion: se VE cargarse
            dischargeTick = age + IONIZATION_TICKS;
            FxDirector.fire(level, FxEvent.IONIZATION, target.position(), 1.0F);
            FxDirector.sound(level, target.position(), ModSounds.IONIZE.get(), 1.8F, 1.0F);
            TitleDirector.actionbar(target, Component.translatable("cataclysm.rayo.charging"));
        }

        if (dischargeTick >= 0 && age >= dischargeTick) {
            dischargeTick = -1;
            discharge(target);
        }

        if (pending == 0 && dischargeTick < 0) {
            if (++idleTicks >= IDLE_GRACE) {
                end("drained");
            }
        } else {
            idleTicks = 0;
        }
    }

    private void discharge(ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        Vec3 at = target.position();
        // rama fractal propia; data = altura de origen para la receta del cliente
        FxDirector.fire(level, FxEvent.LIGHTNING_BOLT, at, 1.0F, 40);
        FxDirector.sound(level, at, ModSounds.THUNDER.get(), 3.0F,
                0.9F + random.nextFloat() * 0.2F);
        target.invulnerableTime = 0;
        target.hurt(ModDamageTypes.source(level, ModDamageTypes.LIGHTNING), 6.0F);
        // 2 pops por rayo: la cadena la mantiene la trituradora fusionable
        TotemShredder.start(target, kind().commandName(), 2,
                CataclysmConfig.COMMON.shredIntervalTicks.get());
    }
}
