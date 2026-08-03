package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.entity.CruiseMissileEntity;
import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModEntities;
import com.vvrgs.irontempest.registry.ModSounds;
import com.vvrgs.irontempest.server.util.Announcer;
import com.vvrgs.irontempest.server.util.WarTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tier M (solapable). Offset espacial por índice: cada misil simultáneo
 * nace en un silo rotado 72° alrededor del jugador — 5 a la vez se leen
 * como abanico, no como un amasijo.
 */
public final class CruiseMissileSession extends WarSession {

    private static final int T_LAUNCH = 20;
    private static final int SILO_DIST = 45;

    /** MUTABLE: un TP pre-lanzamiento recoloca el silo en el mundo nuevo. */
    private Vec3 siloPos;
    private CruiseMissileEntity missile;
    private boolean impacted;
    private int impactAge = -1;

    CruiseMissileSession(ServerLevel level, ServerPlayer target, int index) {
        super(level, target, 'M', "cruisemissile");
        double bearing = Math.toRadians(index * 72.0D + level.random.nextDouble() * 30.0D - 15.0D);
        double sx = target.getX() + Math.cos(bearing) * SILO_DIST;
        double sz = target.getZ() + Math.sin(bearing) * SILO_DIST;
        int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(sx), (int) Math.floor(sz));
        this.siloPos = new Vec3(sx, sy, sz);
        this.bossBar = Announcer.bar(Component.translatable("irontempest.bar.cruisemissile"),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        Announcer.title(target, Component.translatable("irontempest.title.cruisemissile"),
                Component.translatable("irontempest.subtitle.cruisemissile"));
    }

    /** Re-anclaje: solo si CAMBIÓ de dimensión (los saltos intra-dim los
     *  resuelve el guiado global con re-adquisición en crucero). */
    @Override
    protected void onTargetRelocated(ServerLevel newLevel, ServerPlayer target) {
        if (this.impacted) {
            return;
        }
        if (this.age <= T_LAUNCH) {
            // Pre-lanzamiento: el silo debe recalcularse en el mundo NUEVO —
            // el heightmap viejo puede quedar dentro de roca (Nether) o sobre
            // el vacío (End) y el misil nacería enterrado o flotando.
            double bearing = newLevel.random.nextDouble() * Math.PI * 2.0D;
            double sx = target.getX() + Math.cos(bearing) * SILO_DIST;
            double sz = target.getZ() + Math.sin(bearing) * SILO_DIST;
            int sy = newLevel.getHeight(Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(sx), (int) Math.floor(sz));
            this.siloPos = new Vec3(sx, sy, sz);
            ModNetwork.fx(newLevel, FxType.SILO_VENT, this.siloPos, 1.0F);
            return;
        }
        if (this.missile != null && !this.missile.isRemoved()
                && this.missile.level() == newLevel) {
            return;
        }
        if (this.missile != null && !this.missile.isRemoved()) {
            this.missile.discard();
        }
        double angle = newLevel.random.nextDouble() * Math.PI * 2.0D;
        Vec3 pos = target.position().add(Math.cos(angle) * 24.0D, 22.0D, Math.sin(angle) * 24.0D);
        CruiseMissileEntity m = ModEntities.CRUISE_MISSILE.get().create(newLevel);
        if (m == null) {
            end("relocate_failed");
            return;
        }
        m.setPos(pos.x, pos.y, pos.z);
        m.launch(this.targetId, this.id);
        m.startInCruise();
        m.setDeltaMovement(target.position().subtract(pos).normalize().scale(1.6D));
        m.alignToVelocity();
        newLevel.addFreshEntity(m);
        WarTeam.join(m);
        this.missile = m;
        ModNetwork.fx(newLevel, FxType.WARP_IN, pos, 1.0F);
    }

    @Override
    protected int maxLifetime() {
        return 900;
    }

    @Override
    protected void tickInternal(@Nullable ServerPlayer target) {
        if (this.age == 1) {
            // Klaxon de silo + venteo de vapor: el aviso llega ANTES que el misil.
            this.level.playSound(null, this.siloPos.x, this.siloPos.y, this.siloPos.z,
                    ModSounds.KLAXON.get(), SoundSource.HOSTILE, 3.5F, 1.0F); // radio 16×vol: debe llegar al objetivo a 45 bl
            ModNetwork.fx(this.level, FxType.SILO_VENT, this.siloPos, 1.0F);
        }
        if (this.age == T_LAUNCH && target != null) {
            launch(target);
        }
        if (this.impacted && this.age - this.impactAge > 40) {
            end("impact_settled");
        }
        if (this.bossBar != null) {
            this.bossBar.setProgress(this.impacted ? 1.0F : Math.min(0.95F, this.age / 380.0F));
        }
        // Cuenta atrás de impacto en actionbar durante la fase terminal.
        if (target != null && this.missile != null && !this.missile.isRemoved()
                && this.missile.getPhase() == CruiseMissileEntity.PHASE_TERMINAL
                && this.age % 10 == 0) {
            int eta = (int) Math.ceil(this.missile.position().distanceTo(target.position()) / 38.0D);
            Announcer.actionbar(target,
                    Component.translatable("irontempest.actionbar.impact_in", Math.max(1, eta)));
        }
        // El misil murió sin avisar (timeout del proyectil, chunk descargado…)
        if (this.age > T_LAUNCH && !this.impacted
                && (this.missile == null || this.missile.isRemoved())) {
            end("missile_lost");
        }
    }

    private void launch(ServerPlayer target) {
        CruiseMissileEntity m = ModEntities.CRUISE_MISSILE.get().create(this.level);
        if (m == null) {
            end("spawn_failed");
            return;
        }
        m.setPos(this.siloPos.x, this.siloPos.y + 1.0D, this.siloPos.z);
        m.launch(target.getUUID(), this.id);
        m.alignToVelocity();
        this.level.addFreshEntity(m);
        WarTeam.join(m);
        this.missile = m;
        this.level.playSound(null, this.siloPos.x, this.siloPos.y, this.siloPos.z,
                ModSounds.MISSILE_LAUNCH.get(), SoundSource.HOSTILE, 3.5F, 1.0F);
    }

    @Override
    protected void onProjectileImpact() {
        this.impacted = true;
        this.impactAge = this.age;
    }

    @Override
    protected void onEnd(String reason) {
        if (this.missile != null && !this.missile.isRemoved()) {
            this.missile.discard();
        }
    }
}
