package com.vvrgs.cataclysm.client.fx;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.compat.AAABridge;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModParticles;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Director de FX del cliente: expande cada FxEvent en una RECETA
 * CRONOLOGICA — scheduler de acciones con delay en ticks: flash -> bola de
 * fuego -> onda -> escombros -> humo, cada una en su tick exacto.
 *
 * Cola con cap (4000) y clear() en logout/unload. Los counts de particulas
 * pasan TODOS por fxDensity (0.25-2.0).
 */
public final class ClientFxDirector {

    private static final int QUEUE_CAP = 4000;

    private static final class Scheduled {
        int delay;
        final Runnable action;

        Scheduled(int delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }
    }

    private static final List<Scheduled> QUEUE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    /** Punto de entrada: packet de evento (o /particle cataclysm:fx_*). */
    public static void onFxEvent(FxEvent type, double x, double y, double z,
                                 float intensity, int seed, int data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // el effek del manifest ELEVA los FX nativos cuando esta mapeado
        ResourceLocation effek = EffekManifest.INSTANCE.effekFor(type);
        if (effek != null) {
            AAABridge.play(mc.level, effek, x, y, z, Math.max(0.4F, intensity), data);
        }
        switch (type) {
            case TELEGRAPH -> recipeTelegraph(x, y, z, intensity, data);
            case METEOR_IMPACT -> recipeMeteorImpact(x, y, z, intensity);
            case IONIZATION -> recipeIonization(x, y, z, intensity);
            case LIGHTNING_BOLT -> recipeLightning(x, y, z, intensity, data);
            case FIRE_BURST -> recipeFireBurst(x, y, z, intensity);
            case EMBER_FIELD -> recipeEmberField(x, y, z, intensity);
            case SMOKE_COLUMN -> recipeSmokeColumn(x, y, z, intensity);
            case RAIN_CELL -> recipeRainCell(x, y, z, intensity, Math.max(4, data));
            case WIND_GUST -> recipeWindGust(x, y, z, intensity, data);
            case QUAKE_DUST -> recipeQuakeDust(x, y, z, intensity);
            case FISSURE_BURST -> recipeFissureBurst(x, y, z, intensity, data);
            case TORNADO_DEBRIS -> recipeTornadoDebris(x, y, z, intensity, data);
            case TSUNAMI_SPRAY -> recipeSpray(x, y, z, intensity);
            case LAVA_FOUNTAIN -> recipeLavaFountain(x, y, z, intensity);
            case ERUPTION_BLAST -> recipeEruption(x, y, z, intensity);
            case PYROCLASTIC_FRONT -> recipePyroclastic(x, y, z, intensity, data);
            case ASH_FALL -> recipeAshFall(x, y, z, intensity, Math.max(6, data));
            case IMPACT_FLASH -> recipeImpactFlash(x, y, z, intensity);
            case SHOCKWAVE_RING -> recipeShockwaveRing(x, y, z, intensity, Math.max(10, data));
            case IMPACT_MUSHROOM -> recipeMushroom(x, y, z, intensity);
            case EJECTA -> recipeEjecta(x, y, z, intensity);
            case TOTEM_POP -> recipeTotemPop(x, y, z, intensity);
            case EXECUTION_ANCHOR -> recipeExecutionAnchor(x, y, z, intensity);
            case APOCALYPSE_OMEN -> recipeOmen(x, y, z, intensity);
        }
    }

    // ==== infraestructura ====

    static void schedule(int delayTicks, Runnable action) {
        if (QUEUE.size() >= QUEUE_CAP) {
            return; // cap: bajo spam extremo se pierden colas, no el frame
        }
        QUEUE.add(new Scheduled(delayTicks, action));
    }

    public static void tick() {
        if (QUEUE.isEmpty()) {
            return;
        }
        // snapshot: una accion puede programar mas acciones
        List<Scheduled> due = new ArrayList<>();
        Iterator<Scheduled> it = QUEUE.iterator();
        while (it.hasNext()) {
            Scheduled s = it.next();
            if (--s.delay <= 0) {
                due.add(s);
                it.remove();
            }
        }
        for (Scheduled s : due) {
            try {
                s.action.run();
            } catch (Exception ignored) {
                // una receta rota jamas tumba el frame; sin log-spam por tick
            }
        }
    }

    public static void clearAll() {
        QUEUE.clear();
        ScreenShake.clear();
        FlashOverlay.clear();
        PostShaders.stopAll();
    }

    private static int count(int base) {
        return Math.max(1, (int) Math.round(base * CataclysmConfig.CLIENT.fxDensity.get()));
    }

    private static void particle(ParticleOptions type, double x, double y, double z,
                                 double dx, double dy, double dz) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.addParticle(type, x, y, z, dx, dy, dz);
        }
    }

    private static Vec3 cam() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    private static void localSound(double x, double y, double z,
                                   net.minecraft.sounds.SoundEvent sound, float vol, float pitch) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.playLocalSound(x, y, z, sound, SoundSource.WEATHER, vol, pitch, false);
        }
    }

    // ==== recetas ====

    /** Anillo dorado pulsante + motas convergentes: receta visual DISTINTA de una explosion. */
    private static void recipeTelegraph(double x, double y, double z, float intensity, int durationTicks) {
        int pulses = Math.max(2, durationTicks / 6);
        for (int p = 0; p < pulses; p++) {
            schedule(p * 6, () -> {
                int n = count(10);
                for (int i = 0; i < n; i++) {
                    double a = (Math.PI * 2.0D * i) / n;
                    // anillo en el suelo
                    particle(ModParticles.TELEGRAPH.get(),
                            x + Math.cos(a) * 2.0D, y + 0.15D, z + Math.sin(a) * 2.0D,
                            0.0D, 0.0D, 0.0D);
                    // motas convergentes
                    if (i % 2 == 0) {
                        particle(ModParticles.TELEGRAPH.get(),
                                x + Math.cos(a) * 3.2D, y + 0.4D, z + Math.sin(a) * 3.2D,
                                -Math.cos(a) * 0.12D, 0.02D, -Math.sin(a) * 0.12D);
                    }
                }
            });
        }
    }

    private static void recipeMeteorImpact(double x, double y, double z, float intensity) {
        Vec3 cam = cam();
        FlashOverlay.flashAt(x, y, z, cam.x, cam.y, cam.z, 0.5F * intensity, 80.0D, 0xFFE8C0);
        ScreenShake.addAt(x, y, z, cam.x, cam.y, cam.z, 0.45F * intensity, 90.0D);
        // t0: chispazo inicial
        int sparks = count(12);
        for (int i = 0; i < sparks; i++) {
            particle(ModParticles.EMBER.get(), x, y + 0.3D, z,
                    RANDOM.nextGaussian() * 0.35D, RANDOM.nextDouble() * 0.5D + 0.15D,
                    RANDOM.nextGaussian() * 0.35D);
        }
        // t+2: bola de fuego
        schedule(2, () -> {
            int n = count(16);
            for (int i = 0; i < n; i++) {
                Vec3 dir = randomSphere();
                particle(ModParticles.PLASMA.get(), x, y + 0.5D, z,
                        dir.x * 0.45D, Math.abs(dir.y) * 0.4D, dir.z * 0.45D);
            }
        });
        // t+4: escombros balisticos
        schedule(4, () -> {
            int n = count(10);
            for (int i = 0; i < n; i++) {
                particle(ModParticles.DUST.get(), x, y + 0.4D, z,
                        RANDOM.nextGaussian() * 0.5D, RANDOM.nextDouble() * 0.8D + 0.3D,
                        RANDOM.nextGaussian() * 0.5D);
                if (i % 2 == 0) {
                    particle(ModParticles.DEBRIS.get(), x, y + 0.5D, z,
                            RANDOM.nextGaussian() * 0.4D, RANDOM.nextDouble() * 0.7D + 0.2D,
                            RANDOM.nextGaussian() * 0.4D);
                }
            }
        });
        // t+7: humo que se queda
        schedule(7, () -> {
            int n = count(9);
            for (int i = 0; i < n; i++) {
                particle(ModParticles.SMOKE.get(),
                        x + RANDOM.nextGaussian() * 1.2D, y + 0.6D, z + RANDOM.nextGaussian() * 1.2D,
                        RANDOM.nextGaussian() * 0.02D, 0.08D + RANDOM.nextDouble() * 0.05D,
                        RANDOM.nextGaussian() * 0.02D);
            }
        });
    }

    private static void recipeIonization(double x, double y, double z, float intensity) {
        for (int batch = 0; batch < 4; batch++) {
            schedule(batch * 2, () -> {
                int n = count(6);
                for (int i = 0; i < n; i++) {
                    particle(ModParticles.SPARK.get(),
                            x + RANDOM.nextGaussian() * 1.4D,
                            y + RANDOM.nextDouble() * 1.5D,
                            z + RANDOM.nextGaussian() * 1.4D,
                            0.0D, 0.15D + RANDOM.nextDouble() * 0.1D, 0.0D);
                }
            });
        }
    }

    /** Rama fractal propia: polilinea con jitter + 2 ramas secundarias. */
    private static void recipeLightning(double x, double y, double z, float intensity, int originHeight) {
        Vec3 cam = cam();
        FlashOverlay.flashAt(x, y, z, cam.x, cam.y, cam.z, 0.65F * intensity, 100.0D, 0xE8F0FF);
        ScreenShake.addAt(x, y, z, cam.x, cam.y, cam.z, 0.35F * intensity, 80.0D);
        drawBolt(x, y + Math.max(20, originHeight), z, x, y, z, 0.30F, true);
        // trueno retardado por distancia (el crack cercano llega del server)
        double dist = cam.distanceTo(new Vec3(x, y, z));
        if (dist > 24.0D) {
            int delay = (int) (dist / 6.0D);
            schedule(delay, () -> localSound(x, y, z, ModSounds.THUNDER.get(), 2.5F, 0.55F));
        }
    }

    private static void drawBolt(double x0, double y0, double z0,
                                 double x1, double y1, double z1, float jitter, boolean branches) {
        int segments = 14;
        double px = x0;
        double py = y0;
        double pz = z0;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double nx = Mth.lerp(t, x0, x1) + RANDOM.nextGaussian() * jitter * (1.0D - t) * 6.0D;
            double ny = Mth.lerp(t, y0, y1);
            double nz = Mth.lerp(t, z0, z1) + RANDOM.nextGaussian() * jitter * (1.0D - t) * 6.0D;
            // motas a lo largo del segmento
            int steps = count(4);
            for (int s = 0; s < steps; s++) {
                double st = s / (double) steps;
                particle(ModParticles.SPARK.get(),
                        Mth.lerp(st, px, nx), Mth.lerp(st, py, ny), Mth.lerp(st, pz, nz),
                        0.0D, 0.0D, 0.0D);
            }
            // ramas secundarias a media altura
            if (branches && (i == 5 || i == 9) && RANDOM.nextFloat() < 0.8F) {
                drawBolt(nx, ny, nz,
                        nx + RANDOM.nextGaussian() * 5.0D, ny - 6.0D - RANDOM.nextDouble() * 4.0D,
                        nz + RANDOM.nextGaussian() * 5.0D, jitter * 0.7F, false);
            }
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    private static void recipeFireBurst(double x, double y, double z, float intensity) {
        int n = count(10);
        for (int i = 0; i < n; i++) {
            particle(ModParticles.EMBER.get(),
                    x + RANDOM.nextGaussian() * 1.5D, y + 0.2D, z + RANDOM.nextGaussian() * 1.5D,
                    RANDOM.nextGaussian() * 0.04D, 0.12D + RANDOM.nextDouble() * 0.12D,
                    RANDOM.nextGaussian() * 0.04D);
        }
        schedule(3, () -> {
            int m = count(5);
            for (int i = 0; i < m; i++) {
                particle(ModParticles.SMOKE.get(),
                        x + RANDOM.nextGaussian() * 1.2D, y + 1.0D, z + RANDOM.nextGaussian() * 1.2D,
                        0.0D, 0.06D, 0.0D);
            }
        });
    }

    private static void recipeEmberField(double x, double y, double z, float intensity) {
        int n = count(8);
        for (int i = 0; i < n; i++) {
            particle(ModParticles.EMBER.get(),
                    x + RANDOM.nextGaussian() * 4.0D, y + RANDOM.nextDouble() * 2.0D,
                    z + RANDOM.nextGaussian() * 4.0D,
                    RANDOM.nextGaussian() * 0.02D, 0.08D + RANDOM.nextDouble() * 0.06D,
                    RANDOM.nextGaussian() * 0.02D);
        }
    }

    private static void recipeSmokeColumn(double x, double y, double z, float intensity) {
        for (int batch = 0; batch < 16; batch++) {
            schedule(batch * 4, () -> {
                int n = count(4);
                for (int i = 0; i < n; i++) {
                    particle(ModParticles.SMOKE.get(),
                            x + RANDOM.nextGaussian() * 0.8D, y + 0.5D, z + RANDOM.nextGaussian() * 0.8D,
                            RANDOM.nextGaussian() * 0.015D, 0.18D + RANDOM.nextDouble() * 0.08D,
                            RANDOM.nextGaussian() * 0.015D);
                }
            });
        }
    }

    private static void recipeRainCell(double x, double y, double z, float intensity, int radius) {
        int drops = count(24);
        for (int i = 0; i < drops; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(RANDOM.nextDouble()) * radius;
            particle(ModParticles.RAINDROP.get(),
                    x + Math.cos(a) * r, y + RANDOM.nextDouble() * 2.0D, z + Math.sin(a) * r,
                    0.0D, -1.1D, 0.0D);
        }
        // techo de nube negra
        int puffs = count(5);
        for (int i = 0; i < puffs; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(RANDOM.nextDouble()) * radius * 0.8D;
            particle(ModParticles.SMOKE.get(),
                    x + Math.cos(a) * r, y + 2.5D + RANDOM.nextDouble(), z + Math.sin(a) * r,
                    RANDOM.nextGaussian() * 0.01D, 0.0D, RANDOM.nextGaussian() * 0.01D);
        }
    }

    private static void recipeWindGust(double x, double y, double z, float intensity, int yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double dx = Math.cos(yaw);
        double dz = Math.sin(yaw);
        for (int batch = 0; batch < 3; batch++) {
            schedule(batch * 3, () -> {
                int n = count(8);
                for (int i = 0; i < n; i++) {
                    particle(ModParticles.DUST.get(),
                            x - dx * 8.0D + RANDOM.nextGaussian() * 5.0D,
                            y + 0.5D + RANDOM.nextDouble() * 2.5D,
                            z - dz * 8.0D + RANDOM.nextGaussian() * 5.0D,
                            dx * (0.6D + RANDOM.nextDouble() * 0.4D), 0.02D,
                            dz * (0.6D + RANDOM.nextDouble() * 0.4D));
                }
            });
        }
    }

    private static void recipeQuakeDust(double x, double y, double z, float intensity) {
        Vec3 cam = cam();
        ScreenShake.addAt(x, y, z, cam.x, cam.y, cam.z, 0.22F * intensity, 60.0D);
        int n = count(9);
        for (int i = 0; i < n; i++) {
            particle(ModParticles.DUST.get(),
                    x + RANDOM.nextGaussian() * 5.0D, y + 0.2D, z + RANDOM.nextGaussian() * 5.0D,
                    RANDOM.nextGaussian() * 0.05D, 0.10D + RANDOM.nextDouble() * 0.12D,
                    RANDOM.nextGaussian() * 0.05D);
        }
    }

    private static void recipeFissureBurst(double x, double y, double z, float intensity, int yawDegrees) {
        Vec3 cam = cam();
        ScreenShake.addAt(x, y, z, cam.x, cam.y, cam.z, 0.18F, 50.0D);
        int n = count(14);
        for (int i = 0; i < n; i++) {
            particle(ModParticles.DUST.get(), x, y + 0.2D, z,
                    RANDOM.nextGaussian() * 0.3D, 0.4D + RANDOM.nextDouble() * 0.5D,
                    RANDOM.nextGaussian() * 0.3D);
        }
        schedule(2, () -> {
            int m = count(4);
            for (int i = 0; i < m; i++) {
                particle(ModParticles.DEBRIS.get(), x, y + 0.3D, z,
                        RANDOM.nextGaussian() * 0.25D, 0.3D + RANDOM.nextDouble() * 0.4D,
                        RANDOM.nextGaussian() * 0.25D);
            }
        });
    }

    private static void recipeTornadoDebris(double x, double y, double z, float intensity, int blockStateId) {
        int n = count(6);
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double r = 2.0D + RANDOM.nextDouble() * 4.0D;
            // velocidad tangencial: los escombros ORBITAN el embudo
            particle(ModParticles.DEBRIS.get(),
                    x + Math.cos(a) * r, y + RANDOM.nextDouble() * 8.0D, z + Math.sin(a) * r,
                    -Math.sin(a) * 0.5D, 0.15D + RANDOM.nextDouble() * 0.2D, Math.cos(a) * 0.5D);
        }
        // nota: blockStateId disponible para tintado futuro
        if (blockStateId > 0 && Block.stateById(blockStateId).isAir()) {
            return;
        }
    }

    private static void recipeSpray(double x, double y, double z, float intensity) {
        int n = count(12);
        for (int i = 0; i < n; i++) {
            particle(ModParticles.SPRAY.get(),
                    x + RANDOM.nextGaussian() * 2.5D, y + RANDOM.nextDouble() * 1.5D,
                    z + RANDOM.nextGaussian() * 2.5D,
                    RANDOM.nextGaussian() * 0.15D, 0.2D + RANDOM.nextDouble() * 0.25D,
                    RANDOM.nextGaussian() * 0.15D);
        }
    }

    private static void recipeLavaFountain(double x, double y, double z, float intensity) {
        int n = count(12);
        for (int i = 0; i < n; i++) {
            particle(ModParticles.LAVA_GLOB.get(), x, y + 0.3D, z,
                    RANDOM.nextGaussian() * 0.22D, 0.55D + RANDOM.nextDouble() * 0.45D,
                    RANDOM.nextGaussian() * 0.22D);
        }
        schedule(3, () -> {
            int m = count(6);
            for (int i = 0; i < m; i++) {
                particle(ModParticles.EMBER.get(), x, y + 1.0D, z,
                        RANDOM.nextGaussian() * 0.15D, 0.3D + RANDOM.nextDouble() * 0.2D,
                        RANDOM.nextGaussian() * 0.15D);
            }
        });
    }

    private static void recipeEruption(double x, double y, double z, float intensity) {
        Vec3 cam = cam();
        FlashOverlay.flashAt(x, y, z, cam.x, cam.y, cam.z, 0.5F, 140.0D, 0xFF9040);
        ScreenShake.addAt(x, y, z, cam.x, cam.y, cam.z, 0.6F, 140.0D);
        PostShaders.trigger(PostShaders.Effect.DISTORTION, 25);
        recipeLavaFountain(x, y, z, intensity);
        schedule(2, () -> recipeLavaFountain(x, y + 2.0D, z, intensity));
        for (int batch = 0; batch < 10; batch++) {
            schedule(4 + batch * 5, () -> {
                int n = count(6);
                for (int i = 0; i < n; i++) {
                    particle(ModParticles.SMOKE.get(),
                            x + RANDOM.nextGaussian() * 1.5D, y + 1.0D, z + RANDOM.nextGaussian() * 1.5D,
                            RANDOM.nextGaussian() * 0.02D, 0.30D + RANDOM.nextDouble() * 0.15D,
                            RANDOM.nextGaussian() * 0.02D);
                }
            });
        }
    }

    private static void recipePyroclastic(double x, double y, double z, float intensity, int yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double dx = Math.cos(yaw);
        double dz = Math.sin(yaw);
        int n = count(12);
        for (int i = 0; i < n; i++) {
            particle(ModParticles.SMOKE.get(),
                    x + RANDOM.nextGaussian() * 2.0D,
                    y + RANDOM.nextDouble() * 5.0D,
                    z + RANDOM.nextGaussian() * 2.0D,
                    dx * 0.35D + RANDOM.nextGaussian() * 0.05D,
                    0.05D + RANDOM.nextDouble() * 0.08D,
                    dz * 0.35D + RANDOM.nextGaussian() * 0.05D);
        }
    }

    private static void recipeAshFall(double x, double y, double z, float intensity, int radius) {
        int n = count(18);
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double r = Math.sqrt(RANDOM.nextDouble()) * radius;
            particle(ModParticles.ASH.get(),
                    x + Math.cos(a) * r, y + 8.0D + RANDOM.nextDouble() * 8.0D, z + Math.sin(a) * r,
                    0.0D, -0.03D, 0.0D);
        }
    }

    private static void recipeImpactFlash(double x, double y, double z, float intensity) {
        Vec3 cam = cam();
        // FLASH BLANCO TOTAL con falloff a 250 bl
        FlashOverlay.flashAt(x, y, z, cam.x, cam.y, cam.z, 1.0F * intensity, 250.0D, 0xFFFFFF);
        ScreenShake.addAt(x, y, z, cam.x, cam.y, cam.z, 1.0F * intensity, 250.0D);
        PostShaders.trigger(PostShaders.Effect.ABERRATION, 35);
        schedule(2, () -> {
            int n = count(24);
            for (int i = 0; i < n; i++) {
                Vec3 dir = randomSphere();
                particle(ModParticles.PLASMA.get(), x, y + 1.0D, z,
                        dir.x * 0.9D, Math.abs(dir.y) * 0.7D, dir.z * 0.9D);
            }
        });
    }

    /** Onda expansiva VISIBLE que avanza como anillo de polvo. */
    private static void recipeShockwaveRing(double x, double y, double z, float intensity, int maxRadius) {
        PostShaders.trigger(PostShaders.Effect.DISTORTION, 30);
        int steps = Math.min(40, maxRadius);
        for (int step = 0; step < steps; step++) {
            final double radius = (step + 1) * (maxRadius / (double) steps);
            schedule(step, () -> {
                int n = count(14);
                for (int i = 0; i < n; i++) {
                    double a = (Math.PI * 2.0D * i) / n + RANDOM.nextDouble() * 0.3D;
                    particle(ModParticles.DUST.get(),
                            x + Math.cos(a) * radius, y + 0.4D, z + Math.sin(a) * radius,
                            Math.cos(a) * 0.25D, 0.06D, Math.sin(a) * 0.25D);
                }
            });
        }
    }

    private static void recipeMushroom(double x, double y, double z, float intensity) {
        // columna densa que sube...
        for (int batch = 0; batch < 24; batch++) {
            final int b = batch;
            schedule(batch * 3, () -> {
                int n = count(5);
                for (int i = 0; i < n; i++) {
                    particle(ModParticles.SMOKE.get(),
                            x + RANDOM.nextGaussian() * 2.0D, y + 0.5D + b * 0.8D,
                            z + RANDOM.nextGaussian() * 2.0D,
                            RANDOM.nextGaussian() * 0.02D, 0.35D, RANDOM.nextGaussian() * 0.02D);
                }
            });
        }
        // ...y el sombrero que se abre arriba
        for (int batch = 0; batch < 12; batch++) {
            schedule(50 + batch * 4, () -> {
                int n = count(8);
                for (int i = 0; i < n; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2.0D;
                    particle(ModParticles.SMOKE.get(),
                            x + Math.cos(a) * 3.0D, y + 22.0D, z + Math.sin(a) * 3.0D,
                            Math.cos(a) * 0.18D, 0.03D, Math.sin(a) * 0.18D);
                }
            });
        }
    }

    private static void recipeEjecta(double x, double y, double z, float intensity) {
        int n = count(10);
        for (int i = 0; i < n; i++) {
            particle(ModParticles.PLASMA.get(), x, y + 1.0D, z,
                    RANDOM.nextGaussian() * 0.5D, 0.6D + RANDOM.nextDouble() * 0.5D,
                    RANDOM.nextGaussian() * 0.5D);
        }
    }

    private static void recipeTotemPop(double x, double y, double z, float intensity) {
        Vec3 cam = cam();
        ScreenShake.addAt(x, y, z, cam.x, cam.y, cam.z, 0.12F, 40.0D);
        int n = count(16);
        for (int i = 0; i < n; i++) {
            Vec3 dir = randomSphere();
            particle(ModParticles.POP_FLASH.get(), x, y + 1.2D, z,
                    dir.x * 0.35D, dir.y * 0.35D + 0.1D, dir.z * 0.35D);
        }
    }

    private static void recipeExecutionAnchor(double x, double y, double z, float intensity) {
        // columna estroboscopica
        int n = count(12);
        for (int i = 0; i < n; i++) {
            particle(ModParticles.SPARK.get(),
                    x + RANDOM.nextGaussian() * 0.4D, y + RANDOM.nextDouble() * 6.0D,
                    z + RANDOM.nextGaussian() * 0.4D,
                    0.0D, 0.05D, 0.0D);
        }
        int ring = count(8);
        for (int i = 0; i < ring; i++) {
            double a = (Math.PI * 2.0D * i) / ring;
            particle(ModParticles.TELEGRAPH.get(),
                    x + Math.cos(a) * 2.5D, y + 0.15D, z + Math.sin(a) * 2.5D,
                    -Math.cos(a) * 0.05D, 0.0D, -Math.sin(a) * 0.05D);
        }
    }

    private static void recipeOmen(double x, double y, double z, float intensity) {
        Vec3 cam = cam();
        ScreenShake.addAt(x, y, z, cam.x, cam.y, cam.z, 0.3F, 120.0D);
        for (int batch = 0; batch < 8; batch++) {
            schedule(batch * 4, () -> {
                int n = count(10);
                for (int i = 0; i < n; i++) {
                    particle(ModParticles.PLASMA.get(),
                            x + RANDOM.nextGaussian() * 6.0D, y + 20.0D + RANDOM.nextDouble() * 10.0D,
                            z + RANDOM.nextGaussian() * 6.0D,
                            0.0D, -0.05D, 0.0D);
                }
            });
        }
    }

    private static Vec3 randomSphere() {
        double u = RANDOM.nextDouble() * 2.0D - 1.0D;
        double a = RANDOM.nextDouble() * Math.PI * 2.0D;
        double r = Math.sqrt(1.0D - u * u);
        return new Vec3(r * Math.cos(a), u, r * Math.sin(a));
    }

    private ClientFxDirector() {
    }
}
