package com.vvrgs.cataclysm.disasters;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.core.Anchors;
import com.vvrgs.cataclysm.core.DisasterSession;
import com.vvrgs.cataclysm.core.Disasters;
import com.vvrgs.cataclysm.core.Afflictions;
import com.vvrgs.cataclysm.core.TerrainBudget;
import com.vvrgs.cataclysm.core.TitleDirector;
import com.vvrgs.cataclysm.core.TotemShredder;
import com.vvrgs.cataclysm.entity.VolcanicBombEntity;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.fx.SkyFx;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import com.vvrgs.cataclysm.registry.ModEntities;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /volcan — tier C con destruccion U. UN VOLCAN NACE cerca del jugador.
 *
 * Coreografia (135 s):
 *   T+0..100     ABOMBAMIENTO: el suelo se hincha por fases (domo de tierra)
 *   T+100..400   CONO: basalto/magma creciendo 15 s, capa a capa (amortizado)
 *   T+400..1300  ERUPCION: boom, bombas balisticas dirigidas AL jugador
 *                (telegraph donde caeran), fuentes de lava, rios de lava
 *                reales pendiente abajo
 *   T+1300..1500 FLUJO PIROCLASTICO: anillo gris que avanza radial y MATA
 *                (salvo totem)
 *   T+1500..2700 LLUVIA DE CENIZA 60 s, cielo oscurecido
 * El cono QUEDA en el mapa.
 */
public class VolcanoSession extends DisasterSession {

    private static final int BULGE_END = 100;
    private static final int CONE_END = 400;
    private static final int ERUPTION_END = 1300;
    private static final int PYRO_END = 1500;
    private static final int DURATION = 2700;

    private static final int CONE_HEIGHT = 15;
    private static final double PYRO_SPEED = 0.4D;
    private static final double PYRO_MAX_RADIUS = 44.0D;

    private record ConeBlock(BlockPos pos, BlockState state, int placeTick) {
    }

    private final List<ConeBlock> conePlan = new ArrayList<>();
    private int coneCursor;
    private final Set<UUID> pyroHit = new HashSet<>();
    private BlockPos summit;
    private Vec3 base;

    public VolcanoSession(MinecraftServer server, ServerPlayer target) {
        super(server, target);
        TitleDirector.title(target,
                Component.translatable(kind().titleKey()),
                Component.translatable(kind().subtitleKey()));
        createBossBar(Component.translatable(kind().bossbarKey()),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10, false);
        planCone(target, 24.0D, 0);
    }

    @Override
    public Disasters kind() {
        return Disasters.VOLCAN;
    }

    @Override
    protected int maxLifetimeTicks() {
        return DURATION + 60;
    }

    /** Precalcula domo + cono, ordenados por tick de colocacion (crece por fases). */
    private void planCone(ServerPlayer target, double distance, int tickBase) {
        conePlan.clear();
        coneCursor = 0;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int cx = Mth.floor(target.getX() + Math.cos(angle) * distance);
        int cz = Mth.floor(target.getZ() + Math.sin(angle) * distance);
        int cy = Anchors.surfaceY(level, cx, cz);
        base = new Vec3(cx + 0.5D, cy, cz + 0.5D);
        summit = new BlockPos(cx, cy + CONE_HEIGHT, cz);

        // ABOMBAMIENTO: domo de tierra r=7, 3 capas, entre tickBase y tickBase+100
        for (int dy = 0; dy < 3; dy++) {
            int r = 7 - dy * 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        conePlan.add(new ConeBlock(new BlockPos(cx + dx, cy + dy, cz + dz),
                                Blocks.COARSE_DIRT.defaultBlockState(),
                                tickBase + dy * 30 + random.nextInt(25)));
                    }
                }
            }
        }
        // CONO: frustum de basalto/blackstone/magma, capa a capa
        for (int y = 0; y < CONE_HEIGHT; y++) {
            double radius = Math.max(1.5D, 11.0D * (1.0D - y / (double) (CONE_HEIGHT + 2)));
            int ir = (int) Math.ceil(radius);
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > radius) {
                        continue;
                    }
                    // hueco interior: chimenea r=2 desde media altura
                    if (y > CONE_HEIGHT / 2 && d < 2.0D) {
                        continue;
                    }
                    float roll = random.nextFloat();
                    BlockState state = roll < 0.12F ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : roll < 0.55F ? Blocks.BASALT.defaultBlockState()
                            : Blocks.BLACKSTONE.defaultBlockState();
                    int placeTick = tickBase + BULGE_END + y * ((CONE_END - BULGE_END) / CONE_HEIGHT)
                            + random.nextInt(12);
                    conePlan.add(new ConeBlock(new BlockPos(cx + dx, cy + y, cz + dz), state, placeTick));
                }
            }
        }
        conePlan.sort((a, b) -> Integer.compare(a.placeTick(), b.placeTick()));
    }

    @Override
    protected void onTick(ServerPlayer target) {
        if (age >= DURATION) {
            end("finished");
            return;
        }
        updateBossbar();

        // construccion amortizada del domo/cono
        boolean built = false;
        while (coneCursor < conePlan.size() && conePlan.get(coneCursor).placeTick() <= age) {
            ConeBlock block = conePlan.get(coneCursor++);
            TerrainBudget.place(level, block.pos(), block.state());
            built = true;
        }
        if (built && age % 20 == 0) {
            FxDirector.fire(level, FxEvent.QUAKE_DUST, base, 0.6F);
            FxDirector.sound(level, base, ModSounds.RUMBLE.get(), 2.5F, 0.6F);
        }

        if (age == CONE_END) {
            // la boca se enciende: lava en la chimenea
            for (int dy = -2; dy <= 0; dy++) {
                TerrainBudget.placeFluid(level, summit.offset(0, dy, 0),
                        Blocks.LAVA.defaultBlockState());
            }
        }

        Vec3 summitPos = new Vec3(summit.getX() + 0.5D, summit.getY() + 1.0D, summit.getZ() + 0.5D);

        // ==== ERUPCION ====
        if (age >= CONE_END && age < ERUPTION_END) {
            if (age == CONE_END + 20) {
                FxDirector.fire(level, FxEvent.ERUPTION_BLAST, summitPos, 1.5F);
                FxDirector.sound(level, summitPos, ModSounds.ERUPTION.get(), 4.0F, 0.9F);
                FxDirector.sky(server, SkyFx.ASH_DARKEN, DURATION - age, 0.5F);
            }
            if (age % 15 == 0) {
                FxDirector.fire(level, FxEvent.LAVA_FOUNTAIN, summitPos, 1.0F);
            }
            if (age % 50 == 0) {
                FxDirector.sound(level, summitPos, ModSounds.LAVA_LOOP.get(), 3.0F, 1.0F);
            }
            // rios de lava pendiente abajo
            if (age % 80 == 0) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                BlockPos vent = summit.offset((int) (Math.cos(a) * 2.0D), -1,
                        (int) (Math.sin(a) * 2.0D));
                TerrainBudget.placeFluid(level, vent, Blocks.LAVA.defaultBlockState());
            }
            // bombas balisticas dirigidas AL jugador con telegraph donde caeran
            if (age % 36 == 0) {
                launchBomb(target, summitPos);
            }
        }

        // ==== FLUJO PIROCLASTICO ====
        if (age >= ERUPTION_END && age < PYRO_END) {
            double radius = (age - ERUPTION_END) * PYRO_SPEED;
            if (radius <= PYRO_MAX_RADIUS) {
                if (age % 2 == 0) {
                    for (int i = 0; i < 8; i++) {
                        double a = (Math.PI * 2.0D * i) / 8.0D + (age % 4) * 0.2D;
                        double px = base.x + Math.cos(a) * radius;
                        double pz = base.z + Math.sin(a) * radius;
                        double py = Anchors.surfaceY(level, Mth.floor(px), Mth.floor(pz));
                        FxDirector.fire(level, FxEvent.PYROCLASTIC_FRONT,
                                new Vec3(px, py, pz), 1.0F, (int) Math.toDegrees(a));
                    }
                }
                // el muro gris MATA (salvo totem) al pasar
                for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(PYRO_MAX_RADIUS + 8.0D))) {
                    double d = Math.sqrt(Math.pow(living.getX() - base.x, 2)
                            + Math.pow(living.getZ() - base.z, 2));
                    if (Math.abs(d - radius) < 3.0D && pyroHit.add(living.getUUID())) {
                        // REGLA SAGRADA: antes de todo golpe letal, subir un
                        // totem a la offhand — sin esto el totem no salva
                        if (living instanceof ServerPlayer player
                                && CataclysmConfig.COMMON.shredAutoRefill.get()) {
                            TotemShredder.refillOffhand(player);
                        }
                        living.invulnerableTime = 0;
                        float pyroDamage = CataclysmConfig.COMMON.lethalStrikes.get()
                                ? 10000.0F : 12.0F;
                        living.hurt(ModDamageTypes.source(level, ModDamageTypes.PYROCLASTIC), pyroDamage);
                    }
                }
            }
            if (age == ERUPTION_END) {
                FxDirector.sound(level, summitPos, ModSounds.ERUPTION.get(), 4.0F, 0.7F);
                TitleDirector.title(target,
                        Component.translatable("cataclysm.volcan.pyro.title"),
                        Component.translatable("cataclysm.volcan.pyro.subtitle"));
            }
        }

        // ==== LLUVIA DE CENIZA ====
        if (age >= PYRO_END) {
            if (age % 12 == 0) {
                FxDirector.fire(level, FxEvent.ASH_FALL, target.position(), 0.8F, 20);
            }
            if (age % 60 == 0) {
                Afflictions.apply(targetId, Afflictions.Kind.ASH, 0.5F, 20 * 10);
            }
        }
    }

    private void launchBomb(ServerPlayer target, Vec3 from) {
        // prediccion: cae donde VA a estar
        Vec3 lead = target.getDeltaMovement().scale(20.0D);
        double sx = target.getX() + lead.x + (random.nextDouble() - 0.5D) * 8.0D;
        double sz = target.getZ() + lead.z + (random.nextDouble() - 0.5D) * 8.0D;
        double sy = Anchors.surfaceY(level, Mth.floor(sx), Mth.floor(sz));
        Vec3 landing = new Vec3(sx, sy, sz);
        int flight = 35;

        FxDirector.fire(level, FxEvent.TELEGRAPH, landing, 1.0F, 25);
        FxDirector.sound(level, landing, ModSounds.TELEGRAPH_PING.get(), 1.5F, 1.2F);

        VolcanicBombEntity bomb = new VolcanicBombEntity(ModEntities.VOLCANIC_BOMB.get(), level);
        bomb.setPos(from.x, from.y, from.z);
        bomb.configure(VolcanicBombEntity.arcVelocity(from, landing, flight), 8.0F);
        level.addFreshEntity(bomb);
    }

    private void updateBossbar() {
        if (bossBar == null) {
            return;
        }
        float progress;
        if (age < CONE_END) {
            progress = (float) coneCursor / Math.max(1, conePlan.size()); // crecimiento real
        } else if (age < ERUPTION_END) {
            progress = (float) (age - CONE_END) / (ERUPTION_END - CONE_END);
        } else {
            progress = 1.0F - (float) (age - ERUPTION_END) / (DURATION - ERUPTION_END);
        }
        bossBar.setProgress(Mth.clamp(progress, 0.0F, 1.0F));
    }

    @Override
    protected void onReanchor(ServerPlayer target, boolean dimensionChange) {
        if (dimensionChange) {
            // el volcan lo PERSIGUE: una boca nueva nace cerca de el en la
            // dimension nueva. JAMAS reutilizar summit/base de la dimension
            // vieja con el level nuevo (coords cross-dim = bug real)
            planCone(target, 18.0D, age + 10);
            pyroHit.clear();
        }
        // sin cambio de dimension el cono original sigue donde estaba:
        // las bombas ya re-apuntan solas al jugador re-resuelto
    }

    @Override
    protected void onEnd(String reason) {
        FxDirector.skyClear(server, SkyFx.ASH_DARKEN);
        // el cono QUEDA en el mapa a proposito
    }
}
