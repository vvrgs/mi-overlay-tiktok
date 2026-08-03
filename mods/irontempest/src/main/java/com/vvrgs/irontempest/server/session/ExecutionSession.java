package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.config.WarConfig;
import com.vvrgs.irontempest.entity.WarshipEntity;
import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModDamage;
import com.vvrgs.irontempest.registry.ModEntities;
import com.vvrgs.irontempest.registry.ModSounds;
import com.vvrgs.irontempest.server.util.Announcer;
import com.vvrgs.irontempest.server.util.DamageUtil;
import com.vvrgs.irontempest.server.util.SustainedDamage;
import com.vvrgs.irontempest.server.util.TerrainSculptor;
import com.vvrgs.irontempest.server.util.TotemShredder;
import com.vvrgs.irontempest.server.util.WarTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * EJECUCIÓN ORBITAL — el regalo más caro. La nave warpea ENCIMA, el haz ancla
 * al jugador (tirón físico hacia el centro, no puedes salir) y la trituradora
 * devora sus tótems uno a uno a cadencia configurable. Si el presupuesto se
 * agota y sigue vivo: clímax de sobrecarga encima. Si se queda sin tótems:
 * muere ahí mismo con su mensaje propio.
 *
 * T+0 warp-in encima → T+25 carga corta → T+65 haz anclado →
 * T+80 trituración → (fin del shred) clímax +40t → warp-out.
 */
public final class ExecutionSession extends WarSession {

    private static final int T_CHARGE = 25;
    private static final int T_BEAM = 65;
    private static final int T_SHRED = 80;
    private static final double ANCHOR_RADIUS = 1.5D;
    private static final double ANCHOR_PULL = 0.22D;

    private WarshipEntity ship;
    private Vec3 anchor = Vec3.ZERO;
    private boolean shredLaunched;
    private int climaxAt = -1;
    private int popsSeen;

    ExecutionSession(ServerLevel level, ServerPlayer target) {
        super(level, target, 'U', "execution");
        this.bossBar = Announcer.bar(Component.translatable("irontempest.bar.execution"),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossBar.setDarkenScreen(true); // el cielo se oscurece: esto es una sentencia
        Announcer.title(target, Component.translatable("irontempest.title.execution"),
                Component.translatable("irontempest.subtitle.execution"));
        spawnShip(target, WarshipEntity.PHASE_WARP_IN, 0.0F);
        SessionManager.broadcastStarted(level, "execution", this.targetName);
    }

    @Override
    protected int maxLifetime() {
        return 900;
    }

    private void spawnShip(ServerPlayer target, byte phase, float charge) {
        Vec3 pos = target.position().add(0.0D, 34.0D, 0.0D);
        WarshipEntity s = ModEntities.WARSHIP.get().create(this.level);
        if (s == null) {
            end("spawn_failed");
            return;
        }
        s.setPos(pos.x, pos.y, pos.z);
        s.setSessionId(this.id);
        // Estado ANTES de addFreshEntity (re-warp a mitad de ejecución).
        s.setPhase(phase);
        s.setCharge(charge);
        this.level.addFreshEntity(s);
        WarTeam.join(s);
        this.ship = s;
        this.anchor = target.position();
        ModNetwork.fx(this.level, FxType.WARP_IN, pos, 2.0F);
        this.level.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.WARP_IN.get(), SoundSource.HOSTILE, 3.0F, 0.85F);
    }

    /** Re-warp ENCIMA del objetivo: la ejecución no se negocia — te sigue al
     *  End, al cielo o a donde te manden tus plugins. */
    @Override
    protected void onTargetRelocated(ServerLevel newLevel, ServerPlayer target) {
        if (this.climaxAt > 0 || this.ship == null) {
            return;
        }
        byte phase = this.ship.getPhase();
        float charge = this.ship.getCharge();
        if (!this.ship.isRemoved()) {
            if (this.ship.level() instanceof ServerLevel oldLevel) {
                ModNetwork.fx(oldLevel, FxType.WARP_OUT, this.ship.position(), 1.5F);
            }
            this.ship.discard();
        }
        spawnShip(target, phase, charge); // re-ancla también: anchor = posición nueva
    }

    @Override
    protected void tickInternal(@Nullable ServerPlayer target) {
        if (this.ship == null || this.ship.isRemoved()) {
            end("ship_lost");
            return;
        }
        if (this.bossBar != null) {
            if (this.shredLaunched) {
                // popsSeen retiene el máximo: la barra no cae a 0 cuando el
                // shred termina justo antes del clímax.
                this.popsSeen = Math.max(this.popsSeen, TotemShredder.popsOf(this.targetId));
                int max = Math.max(1, WarConfig.EXECUTION_MAX_POPS.get());
                this.bossBar.setProgress(Math.min(1.0F, this.popsSeen / (float) max));
                if (this.age % 10 == 0) {
                    this.bossBar.setName(Component.translatable(
                            "irontempest.bar.execution_pops", this.popsSeen));
                }
            } else {
                this.bossBar.setProgress(0.1F * Math.min(1.0F, this.age / (float) T_SHRED));
            }
        }
        if (this.age == T_CHARGE) {
            this.ship.setPhase(WarshipEntity.PHASE_CHARGE);
            this.level.playSound(null, this.ship.getX(), this.ship.getY(), this.ship.getZ(),
                    ModSounds.LASER_CHARGE.get(), SoundSource.HOSTILE, 3.0F, 1.25F);
        }
        if (this.age > T_CHARGE && this.age < T_BEAM) {
            this.ship.setCharge((this.age - T_CHARGE) / (float) (T_BEAM - T_CHARGE));
            if (this.age % 8 == 0) {
                ModNetwork.fx(this.level, FxType.CHARGE_BURST, this.ship.cannonEmitter(),
                        (this.age - T_CHARGE) / (float) (T_BEAM - T_CHARGE));
            }
        }
        if (this.age == T_BEAM) {
            this.ship.setPhase(WarshipEntity.PHASE_BEAM);
            this.anchor = target != null ? target.position() : this.anchor;
        }
        if (this.age >= T_BEAM && target != null && this.climaxAt < 0) {
            tickAnchor(target);
        }
        if (this.age == T_SHRED && target != null) {
            TotemShredder.shred(this.level, target, "execution",
                    WarConfig.EXECUTION_MAX_POPS.get());
            this.shredLaunched = true;
        }
        // Presupuesto agotado y sigue vivo → clímax programado.
        if (this.shredLaunched && this.climaxAt < 0 && this.age > T_SHRED + 10
                && !TotemShredder.isActive(this.targetId)) {
            this.climaxAt = this.age + 15;
        }
        if (this.climaxAt > 0 && this.age == this.climaxAt) {
            climax();
        }
        if (this.climaxAt > 0 && this.age >= this.climaxAt + 40) {
            this.ship.discard();
            end("complete");
        }
    }

    /** El haz ANCLA: tirón físico hacia el centro cada tick — no hay huida. */
    private void tickAnchor(ServerPlayer target) {
        Vec3 to = this.anchor.subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        double dist = to.length();
        if (dist > ANCHOR_RADIUS) {
            Vec3 pull = to.normalize().scale(Math.min(ANCHOR_PULL * dist, 0.8D));
            target.setDeltaMovement(target.getDeltaMovement()
                    .multiply(0.6D, 1.0D, 0.6D).add(pull));
            target.hurtMarked = true;
        }
        this.ship.setBeamTarget(this.anchor.add(0.0D, 0.1D, 0.0D));
        if (this.age % 3 == 0) {
            ModNetwork.fx(this.level, FxType.BEAM_SWEEP, this.anchor, 1.2F);
        }
        if (this.age % 20 == 0) {
            TerrainSculptor.crater(this.level, BlockPos.containing(this.anchor),
                    WarConfig.craterRadius(2), true);
        }
    }

    private void climax() {
        // Level de la NAVE, no de la sesión: si un TP re-ancló la sesión durante
        // la ventana del clímax (guard en onTargetRelocated), anchor y nave
        // siguen en el mundo viejo — detonar allí, jamás en coords cruzadas.
        ServerLevel lvl = this.ship.level() instanceof ServerLevel sl ? sl : this.level;
        this.ship.setPhase(WarshipEntity.PHASE_OVERLOAD);
        Vec3 pos = this.anchor;
        ModNetwork.fx(lvl, FxType.OVERLOAD_PULSE, pos, 2.8F);
        lvl.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.EXPLOSION_NEAR.get(), SoundSource.HOSTILE, 3.5F, 0.75F);
        TerrainSculptor.crater(lvl, BlockPos.containing(pos),
                WarConfig.craterRadius(6), true);
        DamageUtil.strikeDamage(lvl, pos, 4.0D, 12.0D, 40.0F,
                ModDamage.SHOCKWAVE, this.ship);
        DamageUtil.blastImpulse(lvl, pos, 12.0D, 2.5D);
        SustainedDamage.zone(lvl, pos, 7.0D, 8.0D, 6.0F, ModDamage.NAPALM, true);
        ModNetwork.fx(lvl, FxType.WARP_OUT, this.ship.position(), 2.0F);
        lvl.playSound(null, this.ship.getX(), this.ship.getY(), this.ship.getZ(),
                ModSounds.WARP_OUT.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
        this.ship.setPhase(WarshipEntity.PHASE_WARP_OUT);
    }

    @Override
    protected void onEnd(String reason) {
        if (this.ship != null && !this.ship.isRemoved()) {
            this.ship.discard();
        }
    }
}
