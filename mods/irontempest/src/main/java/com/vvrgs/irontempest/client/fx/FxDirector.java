package com.vvrgs.irontempest.client.fx;

import com.vvrgs.irontempest.client.effek.EffekBridge;
import com.vvrgs.irontempest.net.ClientboundFxPacket;
import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.registry.ModParticles;
import com.vvrgs.irontempest.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Director de FX del cliente. Cada FxType se expande aquí en una CRONOLOGÍA
 * exacta de partículas propias + shake + flash + post-shader + sonido local
 * (con retardo por distancia) + effek opcional (AAA Particles).
 *
 * Regla de presupuesto: el scheduler tiene tope duro; el tier S spameable
 * genera recetas pequeñas, las cinemáticas pueden ser opulentas.
 */
public final class FxDirector {

    private static final class Scheduled {
        int delay;
        final Runnable action;

        Scheduled(int delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }
    }

    private static final List<Scheduled> QUEUE = new ArrayList<>();
    private static final int MAX_QUEUE = 4000;
    private static final Random RNG = new Random();

    // ------------------------------------------------------------ entrada
    public static void handlePacket(ClientboundFxPacket pkt) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 pos = new Vec3(pkt.x(), pkt.y(), pkt.z());
        Vec3 dir = new Vec3(pkt.dx(), pkt.dy(), pkt.dz());
        float s = pkt.scale();

        EffekBridge.play(pkt.type().name().toLowerCase(Locale.ROOT), pos.x, pos.y, pos.z);

        switch (pkt.type()) {
            case EXPLOSION_LARGE -> explosionLarge(level, pos, s);
            case EXPLOSION_SMALL -> explosionSmall(level, pos);
            case AIRBURST -> airburst(level, pos, s);
            case MUZZLE_FLASH -> muzzleFlash(level, pos, dir);
            case TANK_LANDING -> tankLanding(level, pos, s);
            case WARP_IN -> warp(level, pos, s, true);
            case WARP_OUT -> warp(level, pos, s, false);
            case CHARGE_BURST -> chargeBurst(level, pos, s);
            case BEAM_SWEEP -> beamSweep(level, pos);
            case OVERLOAD_PULSE -> overloadPulse(level, pos, s);
            case ARMAGEDDON_OPENING -> armageddonOpening(level, pos);
            case SCORCH -> scorch(level, pos);
            case SILO_VENT -> siloVent(level, pos, s);
            case DEBRIS_RAIN -> debrisRain(level, pos, s);
            case SHAKE_ONLY -> ScreenShake.addTrauma(s, pos);
        }
    }

    public static void tick() {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Runnable> due = new ArrayList<>();
        QUEUE.removeIf(sch -> {
            if (--sch.delay <= 0) {
                due.add(sch.action);
                return true;
            }
            return false;
        });
        for (Runnable r : due) {
            if (Minecraft.getInstance().level != null) {
                r.run();
            }
        }
    }

    /** Limpieza dura al salir del mundo: las lambdas capturan el ClientLevel viejo. */
    public static void clear() {
        QUEUE.clear();
    }

    private static void schedule(int delayTicks, Runnable action) {
        if (QUEUE.size() >= MAX_QUEUE) {
            return; // presupuesto duro: se sacrifica cola, nunca el framerate
        }
        QUEUE.add(new Scheduled(Math.max(1, delayTicks), action));
    }

    // ------------------------------------------------------------ recetas
    /** Explosión mayor: flash → bola de fuego → anillo → escombros → columna de humo. */
    private static void explosionLarge(ClientLevel level, Vec3 pos, float s) {
        float sc = Math.max(0.8F, s);
        // T+0: flash cegador + chispas balísticas + impacto de cámara
        spawn(level, ModParticles.FLASH.get(), pos, 2, 0.1D, 0.0D);
        sparksRadial(level, pos, (int) (20 * sc), 0.9D, 0.45D);
        fireballCluster(level, pos, (int) (8 * sc), 0.16D);
        ScreenShake.addTrauma(Math.min(0.9F, 0.55F * sc), pos);
        FlashOverlay.flash(0.32F * sc, 0xFFF4E0, pos);
        PostFxManager.shockwave(pos, 0.8F * sc);
        sound(level, pos, ModSounds.EXPLOSION_NEAR.get(), 3.0F, 0.95F + RNG.nextFloat() * 0.1F);
        sound(level, pos, ModSounds.EXPLOSION_FAR.get(), 2.0F, 1.0F);
        // T+2: anillo de choque a ras de suelo + OLA DE POLVO rasante rápida
        schedule(2, () -> {
            spawn(level, ModParticles.SHOCKWAVE.get(), pos.add(0.0D, 0.15D, 0.0D), 1, 0.0D, sc);
            ringSmoke(level, pos, (int) (8 * sc), 0.45D);
            ringSmoke(level, pos, (int) (12 * sc), 0.85D); // ola exterior veloz
        });
        // T+6: segundo anillo (eco de la onda) más tenue
        schedule(6, () -> spawn(level, ModParticles.SHOCKWAVE.get(), pos.add(0.0D, 0.25D, 0.0D), 1, 0.0D, sc * 0.65D));
        // T+3: metralla y brasas
        schedule(3, () -> {
            debrisBurst(level, pos, (int) (14 * sc), 0.85D);
            embersBurst(level, pos, (int) (18 * sc), 0.5D);
        });
        // T+5..T+41: columna de humo negro que crece
        for (int i = 0; i < 12; i++) {
            final int step = i;
            schedule(5 + i * 3, () -> smokeColumn(level, pos, 3, 0.4D + step * 0.09D, true));
        }
        schedule(8, () -> sound(level, pos, ModSounds.DEBRIS_CLANK.get(), 0.9F, 1.0F));
        // Explosiones SECUNDARIAS para las grandes (munición cocinándose)
        if (sc >= 1.6F) {
            for (int i = 0; i < 2; i++) {
                schedule(9 + i * 6, () -> explosionSmall(level,
                        pos.add(RNG.nextGaussian() * 3.0D, 0.5D + RNG.nextDouble() * 1.5D, RNG.nextGaussian() * 3.0D)));
            }
        }
        // T+35: el cráter queda chisporroteando mientras el humo se asienta.
        schedule(35, () -> sound(level, pos, ModSounds.CRATER_SIZZLE.get(), 0.9F, 1.0F));
    }

    private static void explosionSmall(ClientLevel level, Vec3 pos) {
        spawn(level, ModParticles.FLASH.get(), pos, 1, 0.05D, 0.0D);
        sparksRadial(level, pos, 10, 0.7D, 0.4D);
        fireballCluster(level, pos, 4, 0.13D);
        ScreenShake.addTrauma(0.28F, pos);
        PostFxManager.shockwave(pos, 0.35F);
        sound(level, pos, ModSounds.EXPLOSION_NEAR.get(), 1.7F, 1.1F + RNG.nextFloat() * 0.1F);
        schedule(1, () -> spawn(level, ModParticles.SHOCKWAVE.get(), pos.add(0.0D, 0.15D, 0.0D), 1, 0.0D, 0.6D));
        schedule(2, () -> {
            debrisBurst(level, pos, 6, 0.6D);
            embersBurst(level, pos, 8, 0.4D);
        });
        for (int i = 0; i < 6; i++) {
            schedule(3 + i * 3, () -> smokeColumn(level, pos, 2, 0.35D, true));
        }
    }

    /** Detonación aérea: esfera de fuego sin anillo, brasas cayendo. */
    private static void airburst(ClientLevel level, Vec3 pos, float s) {
        float sc = Math.max(1.0F, s);
        spawn(level, ModParticles.FLASH.get(), pos, 2, 0.1D, 0.0D);
        sparksRadial(level, pos, (int) (26 * sc), 1.1D, 0.0D);
        fireballCluster(level, pos, (int) (10 * sc), 0.2D);
        ScreenShake.addTrauma(0.5F * sc, pos);
        FlashOverlay.flash(0.3F * sc, 0xFFF0D8, pos);
        PostFxManager.shockwave(pos, 0.7F * sc);
        sound(level, pos, ModSounds.EXPLOSION_NEAR.get(), 2.8F, 0.9F);
        sound(level, pos, ModSounds.EXPLOSION_FAR.get(), 2.0F, 1.0F);
        schedule(1, () -> spawn(level, ModParticles.SHOCKWAVE.get(), pos, 1, 0.0D, sc * 0.9D));
        schedule(2, () -> {
            for (int i = 0; i < 24 * sc; i++) {
                Vec3 v = randomDir().scale(0.3D + RNG.nextDouble() * 0.4D);
                particle(level, ModParticles.EMBER.get(), pos, v.x, v.y * 0.4D - 0.1D, v.z);
            }
        });
        for (int i = 0; i < 8; i++) {
            schedule(3 + i * 3, () -> smokeColumn(level, pos, 2, 0.3D, true));
        }
    }

    /** Fogonazo de cañón: cono de fuego + anillo de humo + patada de polvo. */
    private static void muzzleFlash(ClientLevel level, Vec3 pos, Vec3 dir) {
        for (int i = 0; i < 6; i++) {
            Vec3 v = cone(dir, 0.18D).scale(0.25D + RNG.nextDouble() * 0.3D);
            particle(level, ModParticles.MUZZLE_FLASH.get(), pos, v.x, v.y, v.z);
        }
        for (int i = 0; i < 8; i++) {
            Vec3 v = cone(dir, 0.10D).scale(0.7D + RNG.nextDouble() * 0.5D);
            particle(level, ModParticles.SPARK.get(), pos, v.x, v.y, v.z);
        }
        spawn(level, ModParticles.FLASH.get(), pos, 1, 0.02D, 0.0D);
        ScreenShake.addTrauma(0.30F, pos);
        // Anillo de humo del freno de boca: velocidades en el plano PERPENDICULAR
        // al disparo (toroide real, no un segundo cono frontal).
        schedule(1, () -> {
            for (int i = 0; i < 10; i++) {
                Vec3 side = randomDir().cross(dir);
                if (side.lengthSqr() < 1.0E-4D) {
                    side = new Vec3(dir.y, dir.z, dir.x).cross(dir);
                }
                Vec3 v = side.normalize().scale(0.12D);
                particle(level, ModParticles.SMOKE.get(), pos, v.x, v.y + 0.03D, v.z);
            }
        });
        // Polvo levantado bajo la boca.
        schedule(2, () -> {
            Vec3 below = pos.add(0.0D, -1.0D, 0.0D).add(dir.scale(1.5D));
            for (int i = 0; i < 5; i++) {
                particle(level, ModParticles.SMOKE.get(), below,
                        RNG.nextGaussian() * 0.05D, 0.04D, RNG.nextGaussian() * 0.05D);
            }
        });
    }

    /** Aterrizaje del tanque: anillo de polvo + chispas + escombros. */
    private static void tankLanding(ClientLevel level, Vec3 pos, float s) {
        ScreenShake.addTrauma(0.55F, pos);
        PostFxManager.shockwave(pos, 0.5F);
        spawn(level, ModParticles.SHOCKWAVE.get(), pos.add(0.0D, 0.2D, 0.0D), 1, 0.0D, s);
        for (int i = 0; i < 26; i++) {
            double angle = i / 26.0D * Math.PI * 2.0D;
            Vec3 v = new Vec3(Math.cos(angle), 0.05D, Math.sin(angle)).scale(0.45D + RNG.nextDouble() * 0.3D);
            particle(level, ModParticles.SMOKE.get(), pos.add(v.scale(1.5D)), v.x, 0.05D, v.z);
        }
        sparksRadial(level, pos, 12, 0.6D, 0.5D);
        schedule(2, () -> debrisBurst(level, pos, 8, 0.5D));
    }

    /** Salto warp: destello + motas implosionando (in) o explotando (out). */
    private static void warp(ClientLevel level, Vec3 pos, float s, boolean in) {
        spawn(level, ModParticles.WARP_FLASH.get(), pos, 1, 0.0D, 0.0D);
        FlashOverlay.flash(0.22F, 0xCCE8FF, pos);
        ScreenShake.addTrauma(0.3F, pos);
        PostFxManager.shockwave(pos, 0.6F * s);
        for (int i = 0; i < 16; i++) {
            double angle = i / 16.0D * Math.PI * 2.0D;
            Vec3 radial = new Vec3(Math.cos(angle), (RNG.nextDouble() - 0.5D) * 0.6D, Math.sin(angle));
            if (in) {
                Vec3 from = pos.add(radial.scale(4.0D));
                Vec3 v = radial.scale(-0.35D);
                particle(level, ModParticles.CHARGE_MOTE.get(), from, v.x, v.y * 0.2D, v.z);
            } else {
                Vec3 v = radial.scale(0.4D);
                particle(level, ModParticles.CHARGE_MOTE.get(), pos, v.x, v.y * 0.2D, v.z);
            }
        }
        schedule(2, () -> spawn(level, ModParticles.WARP_FLASH.get(), pos, 1, 0.0D, 0.0D));
    }

    /** Ráfaga de carga: anillo de motas que convergen al emisor. */
    private static void chargeBurst(ClientLevel level, Vec3 pos, float progress) {
        int n = 8 + (int) (progress * 8);
        for (int i = 0; i < n; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2.0D;
            double dist = 2.5D + RNG.nextDouble() * 3.0D;
            Vec3 from = pos.add(Math.cos(angle) * dist, (RNG.nextDouble() - 0.4D) * 2.5D, Math.sin(angle) * dist);
            Vec3 v = pos.subtract(from).scale(0.10D + progress * 0.08D);
            particle(level, ModParticles.CHARGE_MOTE.get(), from, v.x, v.y, v.z);
        }
        if (progress > 0.7F) {
            spawn(level, ModParticles.FLASH.get(), pos, 1, 0.02D, 0.0D);
        }
    }

    /** Punto de barrido del haz: fuente de chispas + vapor de roca. */
    private static void beamSweep(ClientLevel level, Vec3 pos) {
        for (int i = 0; i < 14; i++) {
            particle(level, ModParticles.SPARK.get(), pos,
                    RNG.nextGaussian() * 0.25D, 0.35D + RNG.nextDouble() * 0.9D, RNG.nextGaussian() * 0.25D);
        }
        embersBurst(level, pos, 8, 0.35D);
        fireballCluster(level, pos, 2, 0.08D);
        for (int i = 0; i < 3; i++) {
            particle(level, ModParticles.SMOKE.get(), pos,
                    RNG.nextGaussian() * 0.06D, 0.12D + RNG.nextDouble() * 0.08D, RNG.nextGaussian() * 0.06D);
        }
        spawn(level, ModParticles.FLASH.get(), pos, 1, 0.01D, 0.0D);
        ScreenShake.addTrauma(0.09F, pos);
    }

    private static void overloadPulse(ClientLevel level, Vec3 pos, float s) {
        explosionLarge(level, pos, s);
        FlashOverlay.flash(0.55F, 0xE8F4FF, pos);
        PostFxManager.shockwave(pos, 1.6F);
        schedule(3, () -> spawn(level, ModParticles.SHOCKWAVE.get(), pos.add(0.0D, 0.3D, 0.0D), 1, 0.0D, s * 1.4D));
        schedule(4, () -> embersBurst(level, pos, 40, 0.8D));
    }

    /** Apertura del armagedón: flash rojo + columnas de humo en anillo. */
    private static void armageddonOpening(ClientLevel level, Vec3 pos) {
        FlashOverlay.flash(0.5F, 0xFF3020);
        ScreenShake.addTraumaDirect(0.55F);
        spawn(level, ModParticles.WARP_FLASH.get(), pos.add(0.0D, 30.0D, 0.0D), 1, 0.0D, 0.0D);
        for (int i = 0; i < 8; i++) {
            final double angle = i / 8.0D * Math.PI * 2.0D;
            schedule(4 + i * 4, () -> {
                Vec3 column = pos.add(Math.cos(angle) * 10.0D, 0.0D, Math.sin(angle) * 10.0D);
                for (int k = 0; k < 6; k++) {
                    particle(level, ModParticles.SMOKE.get(), column,
                            RNG.nextGaussian() * 0.04D, 0.18D + RNG.nextDouble() * 0.1D, RNG.nextGaussian() * 0.04D);
                }
                embersBurst(level, column, 4, 0.3D);
            });
        }
    }

    /** Suelo ardiendo de las zonas de daño sostenido (scale = radio/3). */
    private static void scorch(ClientLevel level, Vec3 pos) {
        double r = 1.0D;
        for (int i = 0; i < 6; i++) {
            Vec3 p = pos.add(RNG.nextGaussian() * r, 0.2D, RNG.nextGaussian() * r);
            particle(level, ModParticles.SMOKE.get(), p,
                    RNG.nextGaussian() * 0.03D, 0.05D + RNG.nextDouble() * 0.04D, RNG.nextGaussian() * 0.03D);
        }
        for (int i = 0; i < 3; i++) {
            Vec3 p = pos.add(RNG.nextGaussian() * r, 0.15D, RNG.nextGaussian() * r);
            particle(level, ModParticles.FIREBALL.get(), p, 0.0D, 0.03D, 0.0D);
        }
        embersBurst(level, pos, 7, 0.3D);
        sparksRadial(level, pos, 3, 0.3D, 0.5D);
        if (RNG.nextFloat() < 0.25F) {
            sound(level, pos, ModSounds.CRATER_SIZZLE.get(), 0.5F, 1.0F + RNG.nextFloat() * 0.2F);
        }
    }

    /** Venteo de vapor (silo / cortina de humo del tanque). */
    private static void siloVent(ClientLevel level, Vec3 pos, float s) {
        int n = (int) (18 * s);
        for (int i = 0; i < n; i++) {
            particle(level, ModParticles.SMOKE.get(),
                    pos.add(RNG.nextGaussian() * 1.2D * s, 0.2D, RNG.nextGaussian() * 1.2D * s),
                    RNG.nextGaussian() * 0.03D, 0.06D + RNG.nextDouble() * 0.08D, RNG.nextGaussian() * 0.03D);
        }
        embersBurst(level, pos, 4, 0.2D);
    }

    /** Lluvia de brasas y chatarra en un radio, durante ~6 s. */
    private static void debrisRain(ClientLevel level, Vec3 pos, float radius) {
        for (int i = 0; i < 30; i++) {
            schedule(2 + i * 4, () -> {
                Vec3 p = pos.add(RNG.nextGaussian() * radius * 0.5D, 16.0D + RNG.nextDouble() * 6.0D,
                        RNG.nextGaussian() * radius * 0.5D);
                for (int k = 0; k < 2; k++) {
                    particle(level, ModParticles.EMBER.get(), p,
                            RNG.nextGaussian() * 0.05D, -0.3D - RNG.nextDouble() * 0.2D, RNG.nextGaussian() * 0.05D);
                }
                particle(level, ModParticles.DEBRIS.get(), p,
                        RNG.nextGaussian() * 0.08D, -0.4D, RNG.nextGaussian() * 0.08D);
                if (RNG.nextFloat() < 0.07F) {
                    sound(level, pos, ModSounds.DEBRIS_CLANK.get(), 0.6F, 0.9F + RNG.nextFloat() * 0.2F);
                }
            });
        }
    }

    // ------------------------------------------------------------ helpers
    private static void particle(ClientLevel level, ParticleOptions type, Vec3 pos,
                                 double vx, double vy, double vz) {
        level.addParticle(type, pos.x, pos.y, pos.z, vx, vy, vz);
    }

    /** n partículas en el punto con jitter y velocidad vertical dada (o scaleParam para shockwave). */
    private static void spawn(ClientLevel level, ParticleOptions type, Vec3 pos, int n,
                              double jitter, double param) {
        for (int i = 0; i < n; i++) {
            level.addParticle(type,
                    pos.x + RNG.nextGaussian() * jitter,
                    pos.y + RNG.nextGaussian() * jitter,
                    pos.z + RNG.nextGaussian() * jitter,
                    0.0D, 0.0D, param);
        }
    }

    private static void sparksRadial(ClientLevel level, Vec3 pos, int n, double speed, double upBias) {
        for (int i = 0; i < n; i++) {
            Vec3 v = randomDir().add(0.0D, upBias, 0.0D).normalize()
                    .scale(speed * (0.5D + RNG.nextDouble() * 0.8D));
            particle(level, ModParticles.SPARK.get(), pos, v.x, v.y, v.z);
        }
    }

    private static void fireballCluster(ClientLevel level, Vec3 pos, int n, double speed) {
        for (int i = 0; i < n; i++) {
            Vec3 v = randomDir().scale(speed * (0.4D + RNG.nextDouble()));
            particle(level, ModParticles.FIREBALL.get(),
                    pos.add(RNG.nextGaussian() * 0.3D, RNG.nextGaussian() * 0.3D, RNG.nextGaussian() * 0.3D),
                    v.x, Math.abs(v.y) * 0.6D + 0.02D, v.z);
        }
    }

    private static void debrisBurst(ClientLevel level, Vec3 pos, int n, double speed) {
        for (int i = 0; i < n; i++) {
            Vec3 v = randomDir().scale(speed * (0.4D + RNG.nextDouble()));
            particle(level, ModParticles.DEBRIS.get(), pos, v.x, Math.abs(v.y) + 0.3D, v.z);
        }
    }

    private static void embersBurst(ClientLevel level, Vec3 pos, int n, double speed) {
        for (int i = 0; i < n; i++) {
            Vec3 v = randomDir().scale(speed * (0.3D + RNG.nextDouble()));
            particle(level, ModParticles.EMBER.get(), pos, v.x, Math.abs(v.y) * 0.8D + 0.1D, v.z);
        }
    }

    private static void ringSmoke(ClientLevel level, Vec3 pos, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double angle = i / (double) n * Math.PI * 2.0D;
            Vec3 v = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)).scale(speed);
            particle(level, ModParticles.SMOKE.get(), pos.add(0.0D, 0.3D, 0.0D), v.x, 0.01D, v.z);
        }
    }

    private static void smokeColumn(ClientLevel level, Vec3 pos, int n, double rise, boolean dark) {
        // Convención SmokeParticle: ySpeed < 0.02 → humo negro de explosión;
        // >= 0.02 → gris claro (polvo/vapor). La subida real la pone la partícula.
        for (int i = 0; i < n; i++) {
            particle(level, ModParticles.SMOKE.get(),
                    pos.add(RNG.nextGaussian() * (0.4D + rise), 0.4D + rise * 2.0D, RNG.nextGaussian() * (0.4D + rise)),
                    RNG.nextGaussian() * 0.02D, dark ? 0.012D : 0.05D, RNG.nextGaussian() * 0.02D);
        }
    }

    private static Vec3 randomDir() {
        Vec3 v;
        do {
            v = new Vec3(RNG.nextGaussian(), RNG.nextGaussian(), RNG.nextGaussian());
        } while (v.lengthSqr() < 1.0E-4D);
        return v.normalize();
    }

    /** Dirección unitaria dentro de un cono alrededor de {@code dir} (spread = apertura relativa). */
    private static Vec3 cone(Vec3 dir, double spread) {
        return dir.add(randomDir().scale(spread)).normalize();
    }

    private static void sound(ClientLevel level, Vec3 pos, SoundEvent event, float vol, float pitch) {
        // distanceDelay=true: el trueno llega tarde si estás lejos. Detalle que se siente.
        level.playLocalSound(pos.x, pos.y, pos.z, event, SoundSource.HOSTILE, vol, pitch, true);
    }

    private FxDirector() {}
}
