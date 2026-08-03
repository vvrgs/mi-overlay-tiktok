package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.config.WarConfig;
import com.vvrgs.irontempest.server.util.TerrainSculptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Registro central de sesiones. Reglas duras del skill:
 * los 3 hooks de cleanup (ServerStopping, PlayerLoggedOut, LivingDeath),
 * colas por tier y exclusión global ULTRA.
 */
@Mod.EventBusSubscriber(modid = IronTempest.MODID)
public final class SessionManager {

    public enum AttackKind { TANK, CRUISE, ROCKET, ORBITAL, ARMAGEDDON, EXECUTION }

    private record Pending(AttackKind kind, UUID playerId) {}

    private static final List<WarSession> SESSIONS = new ArrayList<>();
    private static final Set<Integer> ACTIVE_IDS = new HashSet<>();
    private static final Deque<Pending> PENDING = new ArrayDeque<>();
    private static final int MAX_PENDING_ATTACKS = 64;

    private static int nextId = 1;
    private static boolean ultraActive;

    static int nextSessionId() {
        return nextId++;
    }

    public static boolean isSessionActive(int id) {
        return ACTIVE_IDS.contains(id);
    }

    public static boolean isUltraActive() {
        return ultraActive;
    }

    private static <T extends WarSession> T register(T session) {
        SESSIONS.add(session);
        ACTIVE_IDS.add(session.id);
        return session;
    }

    // ------------------------------------------------------------ lanzadores
    /** Tier S: cola global por jugador; jamás una sesión por comando. */
    public static void rocketRain(ServerLevel level, ServerPlayer target, int salvos) {
        RocketRainSession session = findFor(target.getUUID(), RocketRainSession.class);
        if (session == null) {
            session = register(new RocketRainSession(level, target));
        }
        session.addSalvos(salvos);
    }

    /** Tier M: hasta N simultáneos por jugador, resto FIFO. Devuelve posición en cola (0 = ya). */
    public static int cruiseMissile(ServerLevel level, ServerPlayer target) {
        int active = countFor(target.getUUID(), CruiseMissileSession.class);
        if (active < WarConfig.MAX_CRUISE_PER_PLAYER.get()) {
            register(new CruiseMissileSession(level, target, active));
            return 0;
        }
        return enqueue(AttackKind.CRUISE, target);
    }

    /** Tier C: 1 por jugador, extras en cola. */
    public static int tankBlitz(ServerLevel level, ServerPlayer target) {
        if (countFor(target.getUUID(), TankBlitzSession.class) == 0) {
            register(new TankBlitzSession(level, target));
            return 0;
        }
        return enqueue(AttackKind.TANK, target);
    }

    /** Tier C: 1 por jugador, extras en cola. */
    public static int orbitalStrike(ServerLevel level, ServerPlayer target) {
        if (countFor(target.getUUID(), OrbitalStrikeSession.class) == 0) {
            register(new OrbitalStrikeSession(level, target));
            return 0;
        }
        return enqueue(AttackKind.ORBITAL, target);
    }

    /** Ejecución orbital: 1 por jugador, extras en cola. */
    public static int execution(ServerLevel level, ServerPlayer target) {
        if (countFor(target.getUUID(), ExecutionSession.class) == 0) {
            register(new ExecutionSession(level, target));
            return 0;
        }
        return enqueue(AttackKind.EXECUTION, target);
    }

    /** Tier U: exclusión GLOBAL. false = ya hay un armagedón activo. */
    public static boolean armageddon(ServerLevel level, ServerPlayer target) {
        if (ultraActive) {
            return false;
        }
        ultraActive = true;
        register(new ArmageddonSession(level, target));
        return true;
    }

    static void ultraFinished() {
        ultraActive = false;
    }

    /** Arranques forzados (los usa ArmageddonSession, ignoran cupos). */
    static void forceCruise(ServerLevel level, ServerPlayer target, int index) {
        register(new CruiseMissileSession(level, target, index));
    }

    static void forceTank(ServerLevel level, ServerPlayer target) {
        register(new TankBlitzSession(level, target));
    }

    static void forceOrbital(ServerLevel level, ServerPlayer target) {
        register(new OrbitalStrikeSession(level, target));
    }

    public static int stopAll() {
        int n = 0;
        for (WarSession s : new ArrayList<>(SESSIONS)) {
            if (!s.isEnded()) {
                s.end("stopall");
                n++;
            }
        }
        PENDING.clear();
        TerrainSculptor.clearAll();
        com.vvrgs.irontempest.server.util.SustainedDamage.clearAll();
        com.vvrgs.irontempest.server.util.TotemShredder.clearAll();
        com.vvrgs.irontempest.server.util.Announcer.clearAll();
        return n;
    }

    // ------------------------------------------------------------ colas
    private static int enqueue(AttackKind kind, ServerPlayer target) {
        if (PENDING.size() >= MAX_PENDING_ATTACKS) {
            return -1;
        }
        PENDING.add(new Pending(kind, target.getUUID()));
        return PENDING.size();
    }

    private static void processPending(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        Pending head = PENDING.peek();
        ServerPlayer player = server.getPlayerList().getPlayer(head.playerId());
        if (player == null || !player.isAlive() || player.isSpectator()) {
            PENDING.poll();
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean started = switch (head.kind()) {
            case CRUISE -> {
                int active = countFor(player.getUUID(), CruiseMissileSession.class);
                if (active < WarConfig.MAX_CRUISE_PER_PLAYER.get()) {
                    register(new CruiseMissileSession(level, player, active));
                    yield true;
                }
                yield false;
            }
            case TANK -> {
                if (countFor(player.getUUID(), TankBlitzSession.class) == 0) {
                    register(new TankBlitzSession(level, player));
                    yield true;
                }
                yield false;
            }
            case ORBITAL -> {
                if (countFor(player.getUUID(), OrbitalStrikeSession.class) == 0) {
                    register(new OrbitalStrikeSession(level, player));
                    yield true;
                }
                yield false;
            }
            case EXECUTION -> {
                if (countFor(player.getUUID(), ExecutionSession.class) == 0) {
                    register(new ExecutionSession(level, player));
                    yield true;
                }
                yield false;
            }
            default -> {
                yield true; // tipos sin cola: descartar
            }
        };
        if (started) {
            PENDING.poll();
        } else if (PENDING.size() > 1) {
            // Anti head-of-line: si la cabeza no puede arrancar (cupo lleno),
            // rota al final para que los ataques de otros jugadores no se congelen.
            PENDING.add(PENDING.poll());
        }
    }

    // ------------------------------------------------------------ helpers
    @Nullable
    private static <T extends WarSession> T findFor(UUID playerId, Class<T> type) {
        for (WarSession s : SESSIONS) {
            if (!s.isEnded() && type.isInstance(s) && s.targetId().equals(playerId)) {
                return type.cast(s);
            }
        }
        return null;
    }

    private static int countFor(UUID playerId, Class<? extends WarSession> type) {
        int n = 0;
        for (WarSession s : SESSIONS) {
            if (!s.isEnded() && type.isInstance(s) && s.targetId().equals(playerId)) {
                n++;
            }
        }
        return n;
    }

    public static void notifyProjectileImpact(int sessionId) {
        if (sessionId < 0) {
            return;
        }
        for (WarSession s : SESSIONS) {
            if (s.id == sessionId && !s.isEnded()) {
                s.notifyImpact();
                return;
            }
        }
    }

    public static void broadcastStarted(ServerLevel level, String attackKey, String targetName) {
        if (!WarConfig.BROADCAST_MESSAGES.get()) {
            return;
        }
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("irontempest.msg.attack_started",
                        Component.translatable("irontempest.attack." + attackKey), targetName),
                false);
    }

    // ------------------------------------------------------------ tick + cleanup
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Iterar SIEMPRE sobre copia: un tick puede REGISTRAR sesiones nuevas
        // (el armagedón lanza sub-ataques) y mutar la lista viva sería CME.
        for (WarSession s : new ArrayList<>(SESSIONS)) {
            if (!s.isEnded()) {
                try {
                    s.tick();
                } catch (Exception e) {
                    IronTempest.LOGGER.error("[irontempest] excepción en session id={} attack={}: {}",
                            s.id, s.attack, e.toString(), e);
                    s.end("exception");
                }
            }
        }
        SESSIONS.removeIf(s -> {
            if (s.isEnded()) {
                ACTIVE_IDS.remove(s.id);
                return true;
            }
            return false;
        });
        TerrainSculptor.serverTick();
        com.vvrgs.irontempest.server.util.SustainedDamage.serverTick();
        com.vvrgs.irontempest.server.util.TotemShredder.serverTick();
        processPending(event.getServer());
    }

    /** Hook 1/3: parada del servidor. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        int n = stopAll();
        ultraActive = false;
        // El tick loop ya no volverá a purgar: soltar TODO aquí para no retener
        // el ServerLevel del mundo cerrado entre mundos (servidor integrado).
        SESSIONS.clear();
        ACTIVE_IDS.clear();
        IronTempest.LOGGER.info("[irontempest] cleanup por ServerStopping: {} sesiones cerradas", n);
    }

    /** Hook 2/3: desconexión del objetivo. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        for (WarSession s : new ArrayList<>(SESSIONS)) {
            if (!s.isEnded() && s.targetId().equals(id)) {
                s.end("player_logout");
            }
        }
        PENDING.removeIf(p -> p.playerId().equals(id));
        com.vvrgs.irontempest.server.util.SustainedDamage.removeFor(id);
        com.vvrgs.irontempest.server.util.TotemShredder.removeFor(id);
    }

    /** Hook 3/3: muerte del objetivo. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        for (WarSession s : new ArrayList<>(SESSIONS)) {
            if (!s.isEnded() && s.targetId().equals(id)) {
                s.end("target_died");
            }
        }
        PENDING.removeIf(p -> p.playerId().equals(id));
        com.vvrgs.irontempest.server.util.SustainedDamage.removeFor(id);
        com.vvrgs.irontempest.server.util.TotemShredder.removeFor(id);
    }

    private SessionManager() {}
}
