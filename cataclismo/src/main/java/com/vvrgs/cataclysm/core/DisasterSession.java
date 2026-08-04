package com.vvrgs.cataclysm.core;

import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.CataclysmConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Sesion de desastre: una instancia activa dirigida a UN jugador.
 *
 * Contratos duros:
 *  - MAX_LIFETIME_TICKS: jamas ticks huerfanos.
 *  - El target se resuelve GLOBALMENTE cada tick via playerlist (nunca
 *    level.getEntity — se pierde al cambiar de dimension).
 *  - Re-anclaje anti-teleport: cambio de dimension re-ancla SIEMPRE; salto
 *    >48 bloques re-ancla con cooldown de 40 ticks; 5 rebotes rapidos sin
 *    perdon (300 ticks) => end("target_unstable").
 *  - Tras un end() disparado DENTRO del re-anclaje, el tick CORTA.
 *  - end() es idempotente y SIEMPRE limpia la bossbar.
 */
public abstract class DisasterSession {

    /** Salto instantaneo (bloques) que cuenta como relocacion por plugin. */
    private static final double JUMP_THRESHOLD = 48.0D;
    private static final double JUMP_THRESHOLD_SQ = JUMP_THRESHOLD * JUMP_THRESHOLD;
    private static final int JUMP_REANCHOR_COOLDOWN = 40;
    private static final int REBOUND_FORGIVENESS_TICKS = 300;
    private static final int MAX_FAST_REBOUNDS = 5;

    protected final MinecraftServer server;
    public final int id;
    public final UUID targetId;
    public final String targetName;
    protected final RandomSource random;

    /** Dimension y centro actuales del desastre (se re-anclan al perseguir). */
    protected ServerLevel level;
    protected Vec3 anchor;

    /** Offset de abanico para tier M (5 a la vez se leen como abanico, no amasijo). */
    protected int spreadIndex;

    protected int age;
    private boolean ended;

    private ResourceKey<Level> lastDim;
    private Vec3 lastPos;
    private int jumpCooldown;
    private int fastRebounds;
    private int reboundForgiveTimer;

    @Nullable
    protected ServerBossEvent bossBar;

    protected DisasterSession(MinecraftServer server, ServerPlayer target) {
        this.server = server;
        this.id = DisasterManager.nextSessionId();
        this.targetId = target.getUUID();
        this.targetName = target.getGameProfile().getName();
        this.level = target.serverLevel();
        this.anchor = target.position();
        this.random = RandomSource.create();
        this.lastDim = this.level.dimension();
        this.lastPos = target.position();
    }

    /** Identidad del desastre (nombre de comando, tier, factory). */
    public abstract Disasters kind();

    /** Techo duro de vida en ticks: jamas ticks huerfanos. */
    protected abstract int maxLifetimeTicks();

    /** Un tick con el target ya resuelto y el re-anclaje ya aplicado. */
    protected abstract void onTick(ServerPlayer target);

    /**
     * El desastre acaba de re-anclarse (nuevo level/anchor ya seteados).
     * Recolocar entidades set-piece por discard + respawn (NUNCA teleportTo
     * cross-dim). Puede llamar a end() — el tick cortara.
     */
    protected void onReanchor(ServerPlayer target, boolean dimensionChange) {
    }

    /** Limpieza especifica del desastre (entidades, aflicciones, sonidos). */
    protected void onEnd(String reason) {
    }

    public final boolean isEnded() {
        return ended;
    }

    public final int age() {
        return age;
    }

    public final ServerLevel level() {
        return level;
    }

    public final Vec3 anchor() {
        return anchor;
    }

    public final void setSpreadIndex(int index) {
        this.spreadIndex = index;
    }

    /** Llamado por el manager DENTRO de try/catch: una sesion rota jamas tumba el tick. */
    public final void tick() {
        if (ended) {
            return;
        }
        age++;
        if (age > maxLifetimeTicks()) {
            end("timeout");
            return;
        }
        // resolucion GLOBAL del target: sobrevive a cambios de dimension
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null || target.isRemoved() || !target.isAlive()) {
            end("target_missing");
            return;
        }
        trackRelocation(target);
        if (ended) {
            return; // end() dentro del re-anclaje: CORTAR, no tickear la sesion muerta
        }
        attachBossBar(target);
        onTick(target);
    }

    private void trackRelocation(ServerPlayer target) {
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }
        if (reboundForgiveTimer > 0 && --reboundForgiveTimer == 0) {
            fastRebounds = 0; // perdon: el jugador se estabilizo
        }

        ResourceKey<Level> dim = target.serverLevel().dimension();
        Vec3 pos = target.position();
        boolean dimChanged = !dim.equals(lastDim);
        boolean jumped = !dimChanged && lastPos != null
                && pos.distanceToSqr(lastPos) > JUMP_THRESHOLD_SQ;
        lastDim = dim;
        lastPos = pos;

        if (dimChanged) {
            if (!CataclysmConfig.COMMON.followAcrossDimensions.get()) {
                end("target_left_dimension");
                return;
            }
            reanchor(target, true); // el cambio de dimension re-ancla SIEMPRE, sin cooldown
        } else if (jumped && jumpCooldown == 0) {
            jumpCooldown = JUMP_REANCHOR_COOLDOWN;
            reanchor(target, false);
        }
    }

    private void reanchor(ServerPlayer target, boolean dimensionChange) {
        fastRebounds++;
        reboundForgiveTimer = REBOUND_FORGIVENESS_TICKS;
        if (fastRebounds >= MAX_FAST_REBOUNDS) {
            // un plugin lo esta rebotando en bucle: cortar, no spamear entidades
            end("target_unstable");
            return;
        }
        ServerLevel newLevel = target.serverLevel();
        // guard de vacio: en el End no se re-ancla sobre el void
        Vec3 ground = Anchors.findGroundNear(newLevel, target.position(), 48);
        if (ground == null) {
            end("no_ground");
            return;
        }
        this.level = newLevel;
        this.anchor = ground;
        Cataclysm.LOGGER.info("[cataclysm] session reanchor id={} attack={} dim={} pos=({},{},{}) dimChange={}",
                id, kind().commandName(), newLevel.dimension().location(),
                (int) ground.x, (int) ground.y, (int) ground.z, dimensionChange);
        onReanchor(target, dimensionChange);
    }

    /** Crea la bossbar de la sesion (ctor del desastre cinematico). */
    protected final ServerBossEvent createBossBar(Component name, BossEvent.BossBarColor color,
                                                  BossEvent.BossBarOverlay overlay, boolean darkenScreen) {
        ServerBossEvent bar = new ServerBossEvent(name, color, overlay);
        bar.setDarkenScreen(darkenScreen);
        bar.setProgress(0.0F);
        this.bossBar = bar;
        return bar;
    }

    /** Re-engancha al jugador cada tick: idempotente, sobrevive TPs y respawns. */
    private void attachBossBar(ServerPlayer target) {
        if (bossBar == null) {
            return;
        }
        if (!bossBar.getPlayers().contains(target)) {
            // instancia de ServerPlayer nueva (respawn / cambio de dimension):
            // purgar las viejas y enganchar la actual
            bossBar.removeAllPlayers();
            bossBar.addPlayer(target);
        }
    }

    /** Termina la sesion. Idempotente; SIEMPRE limpia la bossbar. */
    public final void end(String reason) {
        if (ended) {
            return;
        }
        ended = true;
        try {
            onEnd(reason);
        } catch (Exception e) {
            Cataclysm.LOGGER.error("[cataclysm] session id={} onEnd fallo (reason={})", id, reason, e);
        }
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar.setVisible(false);
        }
        Cataclysm.LOGGER.info("[cataclysm] session end id={} reason={}", id, reason);
    }

    /** Marker de arranque — lo parsea el overlay del streamer. */
    final void logStart() {
        Cataclysm.LOGGER.info("[cataclysm] session start id={} tier={} attack={} target={}",
                id, kind().tier(), kind().commandName(), targetName);
    }
}
