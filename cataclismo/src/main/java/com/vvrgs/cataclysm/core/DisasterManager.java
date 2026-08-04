package com.vvrgs.cataclysm.core;

import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.entity.DisasterEntity;
import com.vvrgs.cataclysm.fx.FxDirector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registro central de sesiones + tick loop + los 3 hooks de limpieza.
 *
 * Anti-CME: toda iteracion sobre colecciones estaticas va sobre SNAPSHOT
 * (un hurt puede matar -> LivingDeathEvent -> limpieza reentrante DENTRO
 * del mismo tick) y el borrado real es un removeIf al final.
 */
@Mod.EventBusSubscriber(modid = Cataclysm.MODID)
public final class DisasterManager {

    public enum SubmitResult {
        STARTED, QUEUED, QUEUE_FULL
    }

    private static final List<DisasterSession> SESSIONS = new ArrayList<>();
    /** Cola de ataques M/C pendientes por jugador (drena al liberarse cupo). */
    private static final Map<UUID, ArrayDeque<Disasters>> PENDING = new HashMap<>();
    /** Cola global de ultras (exclusion: UNO por servidor). */
    private static final ArrayDeque<QueuedUltra> ULTRA_PENDING = new ArrayDeque<>();
    private static final int ULTRA_PENDING_CAP = 4;

    private record QueuedUltra(Disasters kind, UUID target) {
    }

    private static int nextId = 1;

    static int nextSessionId() {
        return nextId++;
    }

    /** Punto de entrada de los comandos: enruta por tier. */
    public static SubmitResult submit(MinecraftServer server, ServerPlayer target, Disasters kind) {
        return switch (kind.tier()) {
            case S -> submitSpam(server, target, kind);
            case M -> submitOverlappable(server, target, kind);
            case C -> submitCinematic(server, target, kind);
            case U -> submitUltra(server, target, kind);
        };
    }

    private static SubmitResult submitSpam(MinecraftServer server, ServerPlayer target, Disasters kind) {
        for (DisasterSession s : SESSIONS) {
            if (!s.isEnded() && s.kind() == kind && s.targetId.equals(target.getUUID())
                    && s instanceof SpamQueueSession spam) {
                return spam.enqueueSpam(1) ? SubmitResult.QUEUED : SubmitResult.QUEUE_FULL;
            }
        }
        DisasterSession session = start(server, target, kind, 0);
        if (session instanceof SpamQueueSession spam) {
            spam.enqueueSpam(1);
        }
        return SubmitResult.STARTED;
    }

    private static SubmitResult submitOverlappable(MinecraftServer server, ServerPlayer target, Disasters kind) {
        int active = countActiveOfKind(target.getUUID(), kind);
        if (active < CataclysmConfig.COMMON.maxTierMPerPlayer.get()) {
            start(server, target, kind, active);
            return SubmitResult.STARTED;
        }
        return queuePending(target.getUUID(), kind);
    }

    private static SubmitResult submitCinematic(MinecraftServer server, ServerPlayer target, Disasters kind) {
        if (countActiveOfTier(target.getUUID(), Tier.C) < CataclysmConfig.COMMON.maxTierCPerPlayer.get()) {
            start(server, target, kind, 0);
            return SubmitResult.STARTED;
        }
        return queuePending(target.getUUID(), kind);
    }

    private static SubmitResult submitUltra(MinecraftServer server, ServerPlayer target, Disasters kind) {
        if (!isUltraActive()) {
            start(server, target, kind, 0);
            return SubmitResult.STARTED;
        }
        if (ULTRA_PENDING.size() >= ULTRA_PENDING_CAP) {
            return SubmitResult.QUEUE_FULL;
        }
        ULTRA_PENDING.addLast(new QueuedUltra(kind, target.getUUID()));
        return SubmitResult.QUEUED;
    }

    private static SubmitResult queuePending(UUID target, Disasters kind) {
        ArrayDeque<Disasters> queue = PENDING.computeIfAbsent(target, k -> new ArrayDeque<>());
        if (queue.size() >= CataclysmConfig.COMMON.maxPendingPerPlayer.get()) {
            return SubmitResult.QUEUE_FULL;
        }
        queue.addLast(kind);
        return SubmitResult.QUEUED;
    }

    private static DisasterSession start(MinecraftServer server, ServerPlayer target, Disasters kind, int spreadIndex) {
        DisasterSession session = kind.create(server, target);
        session.setSpreadIndex(spreadIndex);
        SESSIONS.add(session);
        session.logStart();
        return session;
    }

    public static boolean isUltraActive() {
        for (DisasterSession s : SESSIONS) {
            if (!s.isEnded() && s.kind().tier() == Tier.U) {
                return true;
            }
        }
        return false;
    }

    public static int countActiveOfKind(UUID target, Disasters kind) {
        int n = 0;
        for (DisasterSession s : SESSIONS) {
            if (!s.isEnded() && s.kind() == kind && s.targetId.equals(target)) {
                n++;
            }
        }
        return n;
    }

    public static int countActiveOfTier(UUID target, Tier tier) {
        int n = 0;
        for (DisasterSession s : SESSIONS) {
            if (!s.isEnded() && s.kind().tier() == tier && s.targetId.equals(target)) {
                n++;
            }
        }
        return n;
    }

    public static int totalActive() {
        return SESSIONS.size();
    }

    /**
     * Arranque directo saltandose cupos (lo usa el director del apocalipsis
     * para encadenar sesiones existentes sin reimplementar nada).
     */
    public static DisasterSession forceStart(MinecraftServer server, ServerPlayer target, Disasters kind) {
        return start(server, target, kind, 0);
    }

    /**
     * Encola N unidades en LA cola spam del jugador para ese tipo (creandola
     * si no existe). Jamas una segunda sesion S del mismo tipo por jugador.
     */
    public static void enqueueSpam(MinecraftServer server, ServerPlayer target, Disasters kind, int amount) {
        for (DisasterSession s : SESSIONS) {
            if (!s.isEnded() && s.kind() == kind && s.targetId.equals(target.getUUID())
                    && s instanceof SpamQueueSession spam) {
                spam.enqueueSpam(amount);
                return;
            }
        }
        DisasterSession session = start(server, target, kind, 0);
        if (session instanceof SpamQueueSession spam) {
            spam.enqueueSpam(amount);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        drainPending(server);

        // SNAPSHOT: un tick de sesion puede matar al target y disparar
        // limpieza reentrante sobre SESSIONS dentro de este mismo bucle
        List<DisasterSession> snapshot = new ArrayList<>(SESSIONS);
        for (DisasterSession session : snapshot) {
            try {
                session.tick();
            } catch (Exception e) {
                Cataclysm.LOGGER.error("[cataclysm] session id={} attack={} exploto en tick, se termina",
                        session.id, session.kind().commandName(), e);
                session.end("exception");
            }
        }
        SESSIONS.removeIf(DisasterSession::isEnded);

        TotemShredder.tick(server);
        Afflictions.tick(server);
        TerrainBudget.drain();
    }

    private static void drainPending(MinecraftServer server) {
        // ultras: exclusion global, drena UNO cuando no hay ultra activo
        if (!isUltraActive() && !ULTRA_PENDING.isEmpty()) {
            QueuedUltra next = ULTRA_PENDING.pollFirst();
            ServerPlayer target = server.getPlayerList().getPlayer(next.target());
            if (target != null && target.isAlive()) {
                start(server, target, next.kind(), 0);
            }
        }
        if (PENDING.isEmpty()) {
            return;
        }
        for (UUID targetId : new ArrayList<>(PENDING.keySet())) {
            ArrayDeque<Disasters> queue = PENDING.get(targetId);
            if (queue == null || queue.isEmpty()) {
                PENDING.remove(targetId);
                continue;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (target == null || !target.isAlive()) {
                PENDING.remove(targetId); // las colas mueren con el jugador
                continue;
            }
            while (!queue.isEmpty()) {
                Disasters head = queue.peekFirst();
                boolean canStart = switch (head.tier()) {
                    case M -> countActiveOfKind(targetId, head) < CataclysmConfig.COMMON.maxTierMPerPlayer.get();
                    case C -> countActiveOfTier(targetId, Tier.C) < CataclysmConfig.COMMON.maxTierCPerPlayer.get();
                    default -> true;
                };
                if (!canStart) {
                    break;
                }
                queue.pollFirst();
                int spread = head.tier() == Tier.M ? countActiveOfKind(targetId, head) : 0;
                start(server, target, head, spread);
            }
            if (queue.isEmpty()) {
                PENDING.remove(targetId);
            }
        }
    }

    /** /cataclysm stopall — corta TODO y limpia TODO. */
    public static int stopAll(MinecraftServer server, String reason) {
        List<DisasterSession> snapshot = new ArrayList<>(SESSIONS);
        for (DisasterSession session : snapshot) {
            session.end(reason);
        }
        // proyectiles sueltos (bolidos, eyecta, bombas volcanicas): son
        // fire-and-forget, no pertenecen a ninguna sesion — barrido explicito
        // con snapshot (discard en caliente muta el entity storage)
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> loose = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof DisasterEntity) {
                    loose.add(entity);
                }
            }
            loose.forEach(Entity::discard);
        }
        int stopped = snapshot.size();
        SESSIONS.removeIf(DisasterSession::isEnded);
        PENDING.clear();
        ULTRA_PENDING.clear();
        TotemShredder.clearAll();
        Afflictions.clearAll();
        TerrainBudget.clear();
        TitleDirector.clearAll();
        GlowTeams.purge(server);
        FxDirector.clearAll(server);
        return stopped;
    }

    // ==== los 3 hooks de limpieza obligatorios ====

    /** Hook 1: el server se apaga — cerrar todo y vaciar colecciones estaticas
     *  (el tick loop ya no volvera a purgar). */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stopAll(event.getServer(), "server_stopping");
        SESSIONS.clear();
        PENDING.clear();
        ULTRA_PENDING.clear();
        nextId = 1;
    }

    /** Al arrancar: purga de teams glow huerfanos de un crash previo. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        GlowTeams.purge(event.getServer());
    }

    /** Hook 2: el target se desconecta — sus sesiones, aflicciones, shreds y
     *  colas mueren con el. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        for (DisasterSession session : new ArrayList<>(SESSIONS)) {
            if (session.targetId.equals(id)) {
                session.end("target_logout");
            }
        }
        SESSIONS.removeIf(DisasterSession::isEnded);
        PENDING.remove(id);
        ULTRA_PENDING.removeIf(q -> q.target().equals(id));
        TotemShredder.cancel(id, "target_logout");
        Afflictions.clear(id);
        TitleDirector.forget(id);
    }

    /** Hook 3: el target muere (sin totem que lo salve) — todo lo suyo termina.
     *  MONITOR: si un plugin de Mohist cancela la muerte (revive), este
     *  listener ni se entera y el show sigue — jamas desmontar por una
     *  muerte cancelada. */
    @SubscribeEvent(priority = EventPriority.MONITOR)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        for (DisasterSession session : new ArrayList<>(SESSIONS)) {
            if (session.targetId.equals(id)) {
                session.end("target_death");
            }
        }
        // el removeIf real lo hara el tick loop (estamos potencialmente
        // DENTRO de un tick de sesion: aqui solo se marca)
        PENDING.remove(id);
        ULTRA_PENDING.removeIf(q -> q.target().equals(id));
        TotemShredder.cancel(id, "target_death");
        Afflictions.clear(id);
    }

    private DisasterManager() {
    }
}
