package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.config.WarConfig;
import java.util.UUID;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Sesión de ataque. Reglas duras del skill:
 * - MAX_LIFETIME_TICKS de seguridad absoluta (nunca ticks huérfanos).
 * - Log-markers "session start/end" para el smoke test.
 * - El target se re-resuelve cada tick; si desaparece, la sesión muere.
 */
public abstract class WarSession {

    public final int id;
    public final char tier;
    public final String attack;
    /** MUTABLE: el re-anclaje sigue al jugador entre dimensiones. */
    protected ServerLevel level;
    protected final UUID targetId;
    protected final String targetName;

    /** Bossbar opcional de la sesión (asignar en el ctor de la subclase). */
    @Nullable
    protected ServerBossEvent bossBar;

    protected int age;
    private boolean ended;
    @Nullable
    private Vec3 lastTargetPos;
    private int relocateCooldown;
    private int relocateCount;
    private int lastRelocateAge;

    protected WarSession(ServerLevel level, ServerPlayer target, char tier, String attack) {
        this.id = SessionManager.nextSessionId();
        this.level = level;
        this.targetId = target.getUUID();
        this.targetName = target.getGameProfile().getName();
        this.tier = tier;
        this.attack = attack;
        IronTempest.LOGGER.info("[irontempest] session start id={} tier={} attack={} target={}",
                this.id, tier, attack, this.targetName);
    }

    /** Vida máxima dura en ticks. */
    protected abstract int maxLifetime();

    /** Tick de la sesión; target ya resuelto (null solo si !requiresTarget). */
    protected abstract void tickInternal(@Nullable ServerPlayer target);

    protected boolean requiresTarget() {
        return true;
    }

    /** Resolución GLOBAL: el jugador se encuentra aunque otro plugin lo haya
     *  mandado al End — la base del re-anclaje. */
    @Nullable
    protected final ServerPlayer target() {
        ServerPlayer player = this.level.getServer().getPlayerList().getPlayer(this.targetId);
        if (player != null && player.isAlive() && !player.isSpectator()) {
            return player;
        }
        return null;
    }

    /** Llamado por SessionManager una vez por tick de servidor. */
    final void tick() {
        if (this.ended) {
            return;
        }
        this.age++;
        if (this.age > maxLifetime()) {
            end("timeout");
            return;
        }
        ServerPlayer target = target();
        if (target == null && requiresTarget()) {
            end("target_gone");
            return;
        }
        if (target != null) {
            maybeRelocate(target);
            if (this.bossBar != null) {
                this.bossBar.addPlayer(target); // idempotente; re-engancha tras TP
            }
        }
        tickInternal(target);
    }

    /**
     * RE-ANCLAJE anti-TP: si otro plugin teletransporta al objetivo (cambio de
     * dimensión o salto > 48 bl en un tick), la sesión se muda con él y avisa a
     * la subclase para recolocar sus entidades (re-drop, re-warp…).
     */
    private void maybeRelocate(ServerPlayer target) {
        if (this.relocateCooldown > 0) {
            this.relocateCooldown--;
        }
        boolean levelChanged = target.serverLevel() != this.level;
        boolean bigJump = !levelChanged && this.lastTargetPos != null
                && target.position().distanceToSqr(this.lastTargetPos) > 48.0D * 48.0D;
        this.lastTargetPos = target.position();
        if (!levelChanged && !bigJump) {
            return;
        }
        if (levelChanged && !WarConfig.FOLLOW_ACROSS_DIMENSIONS.get()) {
            end("target_changed_dimension");
            return;
        }
        // El cambio de dimensión re-ancla SIEMPRE (tickear el level viejo es inútil);
        // el cooldown solo frena saltos de distancia encadenados (explosiones).
        if (!levelChanged && this.relocateCooldown > 0) {
            return;
        }
        // PERDÓN con el tiempo: TPs legítimos espaciados no matan la sesión;
        // solo el thrash rápido (un plugin rebotándolo sin parar) la corta.
        if (this.age - this.lastRelocateAge > 300) {
            this.relocateCount = 0;
        }
        if (++this.relocateCount > 4) {
            end("target_unstable");
            return;
        }
        this.lastRelocateAge = this.age;
        this.relocateCooldown = 40;
        ServerLevel newLevel = target.serverLevel();
        IronTempest.LOGGER.info("[irontempest] session relocate id={} attack={} target={} dim={} jump={}",
                this.id, this.attack, this.targetName,
                newLevel.dimension().location(), bigJump);
        this.level = newLevel;
        onTargetRelocated(newLevel, target);
    }

    /** El objetivo fue teletransportado: recolocar entidades propias. Default: nada. */
    protected void onTargetRelocated(ServerLevel newLevel, ServerPlayer target) {}

    public final void end(String reason) {
        if (this.ended) {
            return;
        }
        this.ended = true;
        if (this.bossBar != null) {
            this.bossBar.removeAllPlayers();
        }
        try {
            onEnd(reason);
        } catch (Exception e) {
            IronTempest.LOGGER.error("[irontempest] error en onEnd de session id={} attack={}: {}",
                    this.id, this.attack, e.toString(), e);
        }
        IronTempest.LOGGER.info("[irontempest] session end id={} reason={}", this.id, reason);
    }

    /** Limpieza específica (descartar entidades propias, etc.). */
    protected void onEnd(String reason) {}

    /** Aviso de impacto de un proyectil de esta sesión. */
    protected void onProjectileImpact() {}

    final void notifyImpact() {
        onProjectileImpact();
    }

    public final boolean isEnded() {
        return this.ended;
    }

    public final UUID targetId() {
        return this.targetId;
    }
}
