package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.entity.CruiseMissileEntity;
import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModEntities;
import com.vvrgs.irontempest.registry.ModSounds;
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

    private final Vec3 siloPos;
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
        this.level.addFreshEntity(m);
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
