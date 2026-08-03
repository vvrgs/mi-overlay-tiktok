package com.vvrgs.irontempest.server.util;

import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.config.WarConfig;
import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModDamage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * TRITURADORA DE TÓTEMS — el corazón del modo streamer. Cada pulso:
 * recarga la offhand desde el inventario (los tótems NO se apilan y solo
 * salvan desde la mano), anula la ventana de invulnerabilidad
 * (invulnerableTime=0 + damage type con bypasses_cooldown) y aplica un golpe
 * letal que consume UN tótem con su animación vanilla completa. Cadencia
 * configurable (default 4 ticks = 5 pops/s).
 *
 * JAMÁS usa bypasses_invulnerability: el tótem SIEMPRE puede salvar — ese es
 * exactamente el espectáculo.
 */
public final class TotemShredder {

    private static final int MAX_ACTIVE_BUDGET = 90;
    private static final int MAX_FAIL_STREAK = 3;

    private static final class Shred {
        ServerLevel level; // MUTABLE: el shred PERSIGUE al jugador entre dimensiones
        final UUID target;
        final String targetName;
        String attackKey;
        int budget;
        int interval;
        int cooldown;
        int pops;
        int failStreak;
        boolean dead;

        Shred(ServerLevel level, ServerPlayer player, String attackKey, int budget, int interval) {
            this.level = level;
            this.target = player.getUUID();
            this.targetName = player.getGameProfile().getName();
            this.attackKey = attackKey;
            this.budget = budget;
            this.interval = interval;
        }
    }

    private static final List<Shred> SHREDS = new ArrayList<>();
    private static boolean warnedLethalOff;

    // ------------------------------------------------------------ API
    /** Añade presupuesto de pops al shred del jugador (se fusiona, cap 90). */
    public static void shred(ServerLevel level, ServerPlayer player, String attackKey, int budget) {
        shred(level, player, attackKey, budget, WarConfig.SHRED_INTERVAL_TICKS.get());
    }

    public static void shred(ServerLevel level, ServerPlayer player, String attackKey,
                             int budget, int interval) {
        if (!WarConfig.shredEnabled() || budget <= 0 || player.isSpectator() || player.isCreative()) {
            return;
        }
        if (!WarConfig.LETHAL_STRIKES.get()) {
            if (!warnedLethalOff) {
                warnedLethalOff = true;
                IronTempest.LOGGER.warn("[irontempest] totemShredder requiere lethalStrikes=true: desactivado");
            }
            return;
        }
        int scaled = Math.max(1, (int) Math.round(budget * WarConfig.SHRED_BUDGET_MULTIPLIER.get()));
        for (Shred s : SHREDS) {
            if (!s.dead && s.target.equals(player.getUUID())) {
                s.budget = Math.min(MAX_ACTIVE_BUDGET, s.budget + scaled);
                s.interval = Math.min(s.interval, interval);
                s.attackKey = attackKey; // el último ataque figura en el log
                return;
            }
        }
        Shred s = new Shred(level, player, attackKey, Math.min(MAX_ACTIVE_BUDGET, scaled), interval);
        SHREDS.add(s);
        IronTempest.LOGGER.info("[irontempest] shred start target={} attack={} budget={}",
                s.targetName, attackKey, s.budget);
    }

    /** Aflige a todos los jugadores en el radio. */
    public static void shredArea(ServerLevel level, Vec3 center, double radius,
                                 String attackKey, int budget) {
        AABB box = new AABB(center, center).inflate(radius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (player.position().distanceTo(center) <= radius) {
                shred(level, player, attackKey, budget);
            }
        }
    }

    public static boolean isActive(UUID target) {
        for (Shred s : SHREDS) {
            if (!s.dead && s.target.equals(target)) {
                return true;
            }
        }
        return false;
    }

    /** Tótems devorados por el shred activo del jugador (0 si no hay shred). */
    public static int popsOf(UUID target) {
        for (Shred s : SHREDS) {
            if (!s.dead && s.target.equals(target)) {
                return s.pops;
            }
        }
        return 0;
    }

    /** Presupuesto restante del shred activo (-1 si no hay shred). */
    public static int budgetLeft(UUID target) {
        for (Shred s : SHREDS) {
            if (!s.dead && s.target.equals(target)) {
                return s.budget;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ tick
    /** Llamar UNA vez por tick de servidor. Itera sobre snapshot: un pulso puede
     *  matar de verdad → LivingDeathEvent → removeFor() DENTRO del tick (anti-CME). */
    public static void serverTick() {
        if (SHREDS.isEmpty()) {
            return;
        }
        for (Shred s : new ArrayList<>(SHREDS)) {
            if (s.dead) {
                continue;
            }
            if (--s.cooldown > 0) {
                continue;
            }
            s.cooldown = s.interval;
            pulse(s);
        }
        SHREDS.removeIf(s -> s.dead);
    }

    private static void pulse(Shred s) {
        // Resolución GLOBAL anti-TP: si un plugin lo mandó al End, el shred
        // se muda con él y sigue devorando tótems allí.
        ServerPlayer player = s.level.getServer().getPlayerList().getPlayer(s.target);
        if (player == null || !player.isAlive() || player.isSpectator()) {
            end(s, "gone");
            return;
        }
        s.level = player.serverLevel();
        if (WarConfig.SHRED_AUTO_REFILL.get()) {
            refillOffhand(player);
        }
        int before = countTotems(player);

        // Cinturón y tirantes: el damage type ya lleva bypasses_cooldown, pero
        // Mohist/plugins pueden tocar la ventana — anularla es gratis.
        player.invulnerableTime = 0;
        DamageUtil.killIfNoTotem(s.level, player, ModDamage.TOTEM_SHRED, null);

        if (!player.isAlive()) {
            // Muerte real (sin tótems): el hook LivingDeath ya nos limpia, pero
            // por si el orden de eventos varía, cerramos aquí también.
            end(s, "dead");
            return;
        }
        int after = countTotems(player);
        if (after < before) {
            s.pops++;
            s.budget--;
            s.failStreak = 0;
            IronTempest.LOGGER.info("[irontempest] totem pop target={} attack={} n={} left={}",
                    s.targetName, s.attackKey, s.pops, after);
            ModNetwork.fx(s.level, FxType.TOTEM_POP,
                    player.position().add(0.0D, 1.2D, 0.0D), 1.0F);
            // Contador en vivo para el show: tótems que le quedan.
            Announcer.actionbar(player,
                    Component.translatable("irontempest.actionbar.totems", after));
            if (s.budget <= 0) {
                end(s, "budget");
            }
        } else {
            // Sin pop y sigue vivo: god-mode de plugin, creativo, cancelaciones…
            if (++s.failStreak >= MAX_FAIL_STREAK) {
                end(s, "noeffect");
            }
        }
    }

    private static void end(Shred s, String reason) {
        s.dead = true;
        IronTempest.LOGGER.info("[irontempest] shred end target={} pops={} reason={}",
                s.targetName, s.pops, reason);
    }

    // ------------------------------------------------------------ tótems
    /** El tótem solo salva desde las manos: recargar la offhand desde el inventario. */
    private static void refillOffhand(ServerPlayer player) {
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                player.getInventory().items.set(i, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, stack);
                return;
            }
        }
    }

    private static int countTotems(ServerPlayer player) {
        int n = 0;
        if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            n++;
        }
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            n++;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------ cleanup
    public static void clearAll() {
        int n = SHREDS.size();
        SHREDS.clear();
        if (n > 0) {
            IronTempest.LOGGER.info("[irontempest] TotemShredder: {} shreds limpiados", n);
        }
    }

    /** Muerte real o logout del objetivo: su shred muere con él. */
    public static void removeFor(UUID target) {
        for (Shred s : SHREDS) {
            if (!s.dead && s.target.equals(target)) {
                end(s, "target_removed");
            }
        }
        SHREDS.removeIf(s -> s.dead);
    }

    private TotemShredder() {}
}
