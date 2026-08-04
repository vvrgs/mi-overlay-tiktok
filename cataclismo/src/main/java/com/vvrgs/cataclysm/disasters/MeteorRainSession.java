package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.Anchors;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.SpamQueueSession;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.entity.BolideEntity;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModEntities;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * /meteoros — tier S. Lluvia de meteoritos pequenos.
 *
 * Coreografia por unidad (cola tipo lluvia, drena 1 cada 4 ticks):
 *   T+0   telegraph: reticula pulsante en el suelo + ping (~1 s de aviso)
 *   T+22  el bolido aparece alto con estela de plasma y spin, cayendo
 *         en diagonal hacia la reticula (~12 ticks de vuelo)
 *   T+34  impacto: flash, crater r=2, zona ardiente 3 s, onda expansiva
 *
 * 100 regalos = 100 incrementos de UNA cola. Jamas 100 sesiones.
 */
public class MeteorRainSession extends DisasterSession implements SpamQueueSession {

    private static final int DRAIN_INTERVAL = 4;
    private static final int TELEGRAPH_LEAD = 22;
    private static final int IDLE_GRACE = 60;

    private record Strike(int fireTick, double x, double y, double z) {
    }

    private int pending;
    private int drainTimer;
    private int idleTicks;
    private final List<Strike> scheduled = new ArrayList<>();

    public MeteorRainSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
    }

    @Override
    public Disasters kind() {
        return Disasters.METEOROS;
    }

    @Override
    protected int maxLifetimeTicks() {
        return 20 * 600; // techo duro; el drenaje normal termina mucho antes
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
        // drenaje a ritmo fijo: se ve como lluvia continua
        if (pending > 0 && ++drainTimer >= DRAIN_INTERVAL) {
            drainTimer = 0;
            pending--;
            scheduleStrike(target);
        }

        // disparo de strikes programados
        Iterator<Strike> it = scheduled.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            if (age >= strike.fireTick()) {
                it.remove();
                launchBolide(strike);
            }
        }

        if (pending > 0 && age % 20 == 0 && pending > 10) {
            TitleDirector.actionbar(target,
                    Component.translatable("cataclysm.meteoros.pending", pending));
        }

        if (pending == 0 && scheduled.isEmpty()) {
            if (++idleTicks >= IDLE_GRACE) {
                end("drained");
            }
        } else {
            idleTicks = 0;
        }
    }

    private void scheduleStrike(ServerPlayer target) {
        // punto de impacto: alrededor del jugador, sesgado a donde se mueve
        Vec3 lead = target.getDeltaMovement().scale(10.0D);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 1.5D + random.nextDouble() * 6.0D;
        double x = target.getX() + lead.x + Math.cos(angle) * radius;
        double z = target.getZ() + lead.z + Math.sin(angle) * radius;
        ServerLevel level = target.serverLevel();
        double y = Anchors.surfaceY(level, (int) Math.floor(x), (int) Math.floor(z));
        Vec3 ground = new Vec3(x, y, z);

        FxDirector.fire(level, FxEvent.TELEGRAPH, ground, 1.0F, 20);
        FxDirector.sound(level, ground, ModSounds.TELEGRAPH_PING.get(), 1.5F,
                1.4F + random.nextFloat() * 0.2F);
        scheduled.add(new Strike(age + TELEGRAPH_LEAD, ground.x, ground.y, ground.z));
    }

    private void launchBolide(Strike strike) {
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            return;
        }
        ServerLevel level = target.serverLevel();
        Vec3 impact = new Vec3(strike.x(), strike.y(), strike.z());
        // entra en diagonal: nacimiento alto y desplazado lateralmente
        double entryAngle = random.nextDouble() * Math.PI * 2.0D;
        Vec3 spawn = impact.add(Math.cos(entryAngle) * 18.0D, 34.0D + random.nextDouble() * 8.0D,
                Math.sin(entryAngle) * 18.0D);
        int flight = 12;
        Vec3 velocity = impact.subtract(spawn).scale(1.0D / flight);

        BolideEntity bolide = new BolideEntity(ModEntities.BOLIDE.get(), level);
        // estado ANTES de addFreshEntity: viaja en el primer packet
        bolide.setPos(spawn.x, spawn.y, spawn.z);
        bolide.configure(velocity, 0.8F + random.nextFloat() * 0.5F, 7.0F, 2, 1.0F);
        level.addFreshEntity(bolide);
        FxDirector.sound(level, spawn, ModSounds.METEOR_WHISTLE.get(), 2.5F,
                1.0F + random.nextFloat() * 0.3F);
    }
}
