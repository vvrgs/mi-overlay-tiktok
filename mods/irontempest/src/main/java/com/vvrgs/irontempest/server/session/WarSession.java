package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.IronTempest;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
    protected final ServerLevel level;
    protected final UUID targetId;
    protected final String targetName;

    protected int age;
    private boolean ended;

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

    @Nullable
    protected final ServerPlayer target() {
        Entity e = this.level.getEntity(this.targetId);
        if (e instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
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
        tickInternal(target);
    }

    public final void end(String reason) {
        if (this.ended) {
            return;
        }
        this.ended = true;
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
