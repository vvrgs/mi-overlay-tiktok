package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.entity.TankEntity;
import com.vvrgs.irontempest.entity.TankShellEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tier C cinemático — patrón GodzillaManager: fases con timeouts duros.
 * DROP (cae de un drop-pod) → HUNT (torreta con traverse realista de
 * 2.2°/tick + designador) → FIRE (guion de 5 disparos con predicción de
 * tiro y compensación balística) → LEAVE (cortina de humo + desguace).
 */
public final class TankBlitzSession extends WarSession {

    private enum Phase { DROP, HUNT, FIRE, LEAVE }

    private static final float TRAVERSE_RATE = 2.2F;
    /** Giro de casco: la mitad del traverse de torreta — el chasis se lee pesado. */
    private static final float HULL_TURN_RATE = 1.1F;
    private static final float SHELL_SPEED = 3.2F;
    private static final double SHELL_GRAVITY = 0.02D;
    /** Guion de disparos: ticks relativos al inicio de FIRE (3 + doble de clímax). */
    private static final int[] SHOT_SCRIPT = {0, 45, 90, 150, 162};
    private static final int LEAVE_DELAY = 40;
    private static final int SMOKE_AT = 10;

    private Phase phase = Phase.DROP;
    private int phaseStart;
    // -1000 y NO Integer.MIN_VALUE: "age - lastShotAge" desbordaría a negativo
    // y el anti-ráfaga bloquearía el primer disparo para siempre.
    private int lastShotAge = -1000;
    private TankEntity tank;
    private float turretYaw;
    private int alignedTicks;
    private int shotsFired;

    TankBlitzSession(ServerLevel level, ServerPlayer target) {
        super(level, target, 'C', "tankblitz");
        this.bossBar = Announcer.bar(Component.translatable("irontempest.bar.tankblitz"),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_6);
        Announcer.title(target, Component.translatable("irontempest.title.tankblitz"),
                Component.translatable("irontempest.subtitle.tankblitz"));
        spawnTank(target);
        SessionManager.broadcastStarted(level, "tankblitz", this.targetName);
    }

    /** Re-anclaje: el tanque re-cae en drop-pod junto al jugador (End incluido). */
    @Override
    protected void onTargetRelocated(ServerLevel newLevel, ServerPlayer target) {
        if (this.phase == Phase.LEAVE) {
            return;
        }
        if (this.tank != null && !this.tank.isRemoved()) {
            this.tank.discard();
        }
        spawnTank(target);
        enterPhase(Phase.DROP);
    }

    @Override
    protected int maxLifetime() {
        return 1200; // 60 s duros
    }

    private void spawnTank(ServerPlayer target) {
        // Guard de vacío (End): probar varios rumbos hasta encontrar suelo real.
        double tx = 0.0D;
        double tz = 0.0D;
        int ty = Integer.MIN_VALUE;
        for (int attempt = 0; attempt < 5; attempt++) {
            double bearing = this.level.random.nextDouble() * Math.PI * 2.0D;
            double dist = attempt < 4 ? 25.0D : 8.0D; // último intento: pegado al jugador
            tx = target.getX() + Math.cos(bearing) * dist;
            tz = target.getZ() + Math.sin(bearing) * dist;
            ty = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(tx), (int) Math.floor(tz));
            if (ty > this.level.getMinBuildHeight() + 1) {
                break;
            }
            ty = Integer.MIN_VALUE;
        }
        if (ty == Integer.MIN_VALUE) {
            end("no_ground"); // el jugador flota sobre el vacío puro
            return;
        }

        TankEntity t = ModEntities.WAR_TANK.get().create(this.level);
        if (t == null) {
            end("spawn_failed");
            return;
        }
        t.setPos(tx, ty + 18.0D, tz);
        t.setSessionId(this.id);
        float yaw = yawTowards(new Vec3(tx, 0, tz), target.position());
        t.setYRot(yaw);
        this.turretYaw = yaw;
        t.setTurretYaw(yaw);
        this.level.addFreshEntity(t);
        WarTeam.join(t); // contorno rojo: visible entre el caos
        this.tank = t;
        // Klaxon de aviso sobre el objetivo: algo cae del cielo.
        this.level.playSound(null, target.getX(), target.getY(), target.getZ(),
                ModSounds.KLAXON.get(), SoundSource.HOSTILE, 1.2F, 1.15F);
    }

    /** Yaw vanilla (0 = +Z) que mira de {@code from} hacia {@code to}. */
    private static float yawTowards(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
    }

    @Override
    protected void tickInternal(@Nullable ServerPlayer target) {
        if (this.tank == null || this.tank.isRemoved()) {
            end("tank_lost");
            return;
        }
        if (this.bossBar != null) {
            float p = switch (this.phase) {
                case DROP -> 0.10F;
                case HUNT -> 0.25F;
                case FIRE -> 0.35F + this.shotsFired * 0.12F;
                case LEAVE -> 1.0F;
            };
            this.bossBar.setProgress(Math.min(1.0F, p));
        }
        switch (this.phase) {
            case DROP -> tickDrop();
            case HUNT -> {
                trackTarget(target);
                tickHunt();
            }
            case FIRE -> {
                trackTarget(target);
                tickFire(target);
            }
            case LEAVE -> tickLeave();
        }
    }

    private void enterPhase(Phase next) {
        this.phase = next;
        this.phaseStart = this.age;
    }

    private int phaseAge() {
        return this.age - this.phaseStart;
    }

    // ------------------------------------------------------------ DROP
    private void tickDrop() {
        if (this.tank.onGround()) {
            land();
            return;
        }
        if (phaseAge() > 100) {
            // Seguro: aterrizaje forzoso (agua, vacío raro…)
            int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                    this.tank.getBlockX(), this.tank.getBlockZ());
            this.tank.setPos(this.tank.getX(), y, this.tank.getZ());
            land();
        }
    }

    private void land() {
        ModNetwork.fx(this.level, FxType.TANK_LANDING, this.tank.position(), 1.5F);
        this.level.playSound(null, this.tank.getX(), this.tank.getY(), this.tank.getZ(),
                ModSounds.TANK_LANDING.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        this.tank.setEngineOn(true);
        enterPhase(Phase.HUNT);
    }

    // ------------------------------------------------------------ tracking
    private void trackTarget(@Nullable ServerPlayer target) {
        if (target == null) {
            return;
        }
        Vec3 aim = leadPoint(target);
        Vec3 muzzleBase = this.tank.position().add(0.0D, 1.35D, 0.0D);
        float desiredYaw = yawTowards(muzzleBase, aim);
        float diff = Mth.wrapDegrees(desiredYaw - this.turretYaw);
        float step = Mth.clamp(diff, -TRAVERSE_RATE, TRAVERSE_RATE);
        this.turretYaw += step;
        this.tank.setTurretYaw(this.turretYaw);

        double horiz = Math.hypot(aim.x - muzzleBase.x, aim.z - muzzleBase.z);
        float desiredPitch = (float) (Mth.atan2(aim.y - muzzleBase.y, horiz) * Mth.RAD_TO_DEG);
        this.tank.setBarrelPitch(Mth.clamp(desiredPitch, -8.0F, 25.0F));

        boolean aligned = Math.abs(diff) < 3.0F;
        this.alignedTicks = aligned ? this.alignedTicks + 1 : 0;
        this.tank.setAiming(aligned);
    }

    /** Predicción de tiro: posición futura + compensación de gravedad del obús. */
    private Vec3 leadPoint(ServerPlayer target) {
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
        double dist = center.distanceTo(this.tank.position().add(0.0D, 1.35D, 0.0D));
        double flight = dist / SHELL_SPEED;
        Vec3 lead = target.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).scale(flight * 0.8D);
        double drop = 0.5D * SHELL_GRAVITY * flight * flight;
        return center.add(lead).add(0.0D, drop, 0.0D);
    }

    // ------------------------------------------------------------ HUNT / FIRE
    private void tickHunt() {
        // El tanque AVANZA hacia el jugador mientras caza (orugas + polvo en
        // cliente), y frena a distancia de tiro o al agotar la fase de avance.
        ServerPlayer target = target();
        // FÍSICA: el casco GIRA hacia el objetivo (solo en HUNT; en FIRE queda
        // fijo como plataforma de tiro). La torreta es yaw absoluto y el
        // renderer resta el yaw del casco: contra-rota sola, puntería intacta.
        if (target != null) {
            float hullDiff = Mth.wrapDegrees(
                    yawTowards(this.tank.position(), target.position()) - this.tank.getYRot());
            this.tank.setYRot(this.tank.getYRot()
                    + Mth.clamp(hullDiff, -HULL_TURN_RATE, HULL_TURN_RATE));
        }
        boolean advance = target != null
                && phaseAge() < 70
                && this.tank.position().distanceTo(target.position()) > 14.0D;
        this.tank.setDriveSpeed(advance ? 0.055D : 0.0D);

        if (this.alignedTicks >= 25 || phaseAge() >= 130) {
            this.tank.setDriveSpeed(0.0D);
            enterPhase(Phase.FIRE);
            // Tras un re-anclaje a mitad de guion: no esperar hitos ya superados.
            if (this.shotsFired > 0) {
                this.phaseStart = this.age
                        - SHOT_SCRIPT[Math.min(this.shotsFired, SHOT_SCRIPT.length - 1)];
            }
        }
    }

    private void tickFire(@Nullable ServerPlayer target) {
        if (this.shotsFired < SHOT_SCRIPT.length
                && phaseAge() >= SHOT_SCRIPT[this.shotsFired]
                && this.alignedTicks >= 2
                && this.age - this.lastShotAge >= 15) { // nunca ráfaga de hitos acumulados
            fireShell(target);
            this.shotsFired++;
            this.lastShotAge = this.age;
        }
        if (this.shotsFired >= SHOT_SCRIPT.length
                && phaseAge() >= SHOT_SCRIPT[SHOT_SCRIPT.length - 1] + LEAVE_DELAY) {
            enterPhase(Phase.LEAVE);
        }
        // Seguro: si nunca se alinea (jugador orbitando), no eternizarse.
        if (phaseAge() > 400) {
            enterPhase(Phase.LEAVE);
        }
    }

    private void fireShell(@Nullable ServerPlayer target) {
        Vec3 muzzle = this.tank.muzzlePoint();
        Vec3 dir = target != null
                ? leadPoint(target).subtract(muzzle).normalize()
                : this.tank.barrelDirection();
        this.tank.markFired();
        ModNetwork.fx(this.level, FxType.MUZZLE_FLASH, muzzle, dir, 1.0F);
        this.level.playSound(null, muzzle.x, muzzle.y, muzzle.z,
                ModSounds.CANNON_FIRE.get(), SoundSource.HOSTILE, 2.5F,
                0.95F + this.level.random.nextFloat() * 0.1F);

        // Telegraph: retícula donde va a caer el obús (~0.6 s de aviso).
        if (target != null) {
            ModNetwork.fx(this.level, FxType.TARGET_MARKER,
                    leadPoint(target).add(0.0D, -1.0D, 0.0D), 0.8F);
        }
        TankShellEntity shell = ModEntities.TANK_SHELL.get().create(this.level);
        if (shell == null) {
            return;
        }
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        shell.setSessionId(this.id);
        shell.setDeltaMovement(dir.scale(SHELL_SPEED));
        shell.alignToVelocity();
        this.level.addFreshEntity(shell);
        WarTeam.join(shell);
    }

    // ------------------------------------------------------------ LEAVE
    private void tickLeave() {
        // Level del TANQUE: un TP durante LEAVE (guard en onTargetRelocated)
        // deja el tanque en el mundo viejo — los FX de retirada van allí.
        ServerLevel lvl = this.tank.level() instanceof ServerLevel sl ? sl : this.level;
        this.tank.setAiming(false);
        if (phaseAge() == SMOKE_AT) {
            // Lanzadores de humo de la torreta: cortina blanca.
            ModNetwork.fx(lvl, FxType.SILO_VENT, this.tank.position().add(0.0D, 1.5D, 0.0D), 2.2F);
        }
        if (phaseAge() >= LEAVE_DELAY + SMOKE_AT) {
            Vec3 pos = this.tank.position().add(0.0D, 1.0D, 0.0D);
            ModNetwork.fx(lvl, FxType.EXPLOSION_LARGE, pos, new Vec3(0.0D, 1.0D, 0.0D), 1.1F);
            ModNetwork.fx(lvl, FxType.DEBRIS_RAIN, pos, 3.0F);
            lvl.playSound(null, pos.x, pos.y, pos.z,
                    ModSounds.DEBRIS_CLANK.get(), SoundSource.HOSTILE, 1.6F, 0.9F);
            this.tank.discard();
            end("complete");
        }
    }

    @Override
    protected void onEnd(String reason) {
        if (this.tank != null && !this.tank.isRemoved()) {
            this.tank.discard();
        }
    }
}
