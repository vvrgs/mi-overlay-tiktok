package com.vvrgs.cataclysm.core;

import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Trituradora de totems — el espectaculo del live son los POPS en cadena.
 *
 * REGLA SAGRADA: jamas bypasses_invulnerability ni nada que impida al totem
 * salvar. El pulso correcto:
 *   1. auto-recargar la offhand con un totem del inventario (no se apilan,
 *      solo salvan desde la mano — sin esto la cadena muere en el 1er pop)
 *   2. invulnerableTime = 0 (cinturon; el damage type ya lleva
 *      bypasses_cooldown de tirantes — en Mohist los plugins tocan esa ventana)
 *   3. killIfNoTotem: hurt de 10000 con damage type propio, NUNCA setHealth(0)
 *   4. pop detectado CONTANDO totems antes/despues (no por eventos, que los
 *      plugins de Mohist los cancelan)
 *   5. log marker + FX + actionbar con el contador
 */
public final class TotemShredder {

    /** Cap de pops activos fusionados por jugador: ni con 10 ultras se pasa. */
    private static final int ACTIVE_POP_CAP = 90;
    private static final int MAX_FAIL_STREAK = 3;
    private static final float KILL_DAMAGE = 10000.0F;

    private static final class Shred {
        final UUID target;
        String attack;
        int budgetLeft;
        int intervalTicks;
        int cooldown;
        int failStreak;
        int popped;

        Shred(UUID target, String attack, int budget, int intervalTicks) {
            this.target = target;
            this.attack = attack;
            this.budgetLeft = budget;
            this.intervalTicks = intervalTicks;
            this.cooldown = intervalTicks;
        }
    }

    private static final Map<UUID, Shred> ACTIVE = new HashMap<>();

    /** Presupuesto efectivo tras multiplicador de config y cap activo. */
    public static int effectiveBudget(int popBudget) {
        return Math.min(ACTIVE_POP_CAP, Math.max(1, (int) Math.round(
                popBudget * CataclysmConfig.COMMON.shredBudgetMultiplier.get())));
    }

    /**
     * Arranca (o fusiona) una trituradora sobre el target.
     * @param popBudget presupuesto de pops del desastre (se multiplica por config)
     * @param intervalTicks cadencia (default 4 = 5 pops/s, minimo 2)
     * @return true si tras la llamada hay shred activo (creado o fusionado);
     *         false si la config lo impide — el llamador NO debe asumir pops
     */
    public static boolean start(ServerPlayer target, String attack, int popBudget, int intervalTicks) {
        if (!CataclysmConfig.COMMON.streamerMode.get()
                || !CataclysmConfig.COMMON.totemShredder.get()
                || !CataclysmConfig.COMMON.lethalStrikes.get()) {
            return false;
        }
        if (!target.isAlive()) {
            // un hurt letal en el mismo camino de codigo pudo matarlo YA:
            // jamas registrar una trituradora zombie sobre un muerto
            return false;
        }
        int budget = effectiveBudget(popBudget);
        int interval = Math.max(2, intervalTicks);
        Shred existing = ACTIVE.get(target.getUUID());
        if (existing != null) {
            // fusion: suma presupuesto con cap, gana la cadencia mas rapida
            existing.budgetLeft = Math.min(ACTIVE_POP_CAP, existing.budgetLeft + budget);
            existing.intervalTicks = Math.min(existing.intervalTicks, interval);
            existing.attack = attack;
            return true;
        }
        Shred shred = new Shred(target.getUUID(), attack, budget, interval);
        ACTIVE.put(target.getUUID(), shred);
        Cataclysm.LOGGER.info("[cataclysm] shred start target={} attack={} budget={} interval={}",
                target.getGameProfile().getName(), attack, shred.budgetLeft, interval);
        return true;
    }

    public static void cancel(UUID target, String reason) {
        Shred removed = ACTIVE.remove(target);
        if (removed != null) {
            Cataclysm.LOGGER.info("[cataclysm] shred end target={} reason={} popped={}",
                    target, reason, removed.popped);
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    public static boolean isActive(UUID target) {
        return ACTIVE.containsKey(target);
    }

    /** Pops ya reventados de la trituradora activa (0 si no hay). */
    public static int getPopped(UUID target) {
        Shred shred = ACTIVE.get(target);
        return shred == null ? 0 : shred.popped;
    }

    /** Presupuesto restante de la trituradora activa (0 si no hay). */
    public static int getBudgetLeft(UUID target) {
        Shred shred = ACTIVE.get(target);
        return shred == null ? 0 : shred.budgetLeft;
    }

    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        // SNAPSHOT: un pulso puede matar -> LivingDeathEvent -> cancel()
        // reentrante sobre ACTIVE dentro de este mismo bucle
        for (Shred shred : new ArrayList<>(ACTIVE.values())) {
            if (!ACTIVE.containsKey(shred.target)) {
                continue; // cancelado reentrantemente en este mismo tick
            }
            // resolucion GLOBAL: la trituradora persigue al End y adonde sea
            ServerPlayer player = server.getPlayerList().getPlayer(shred.target);
            if (player == null || player.isRemoved() || !player.isAlive()) {
                cancel(shred.target, "target_missing");
                continue;
            }
            if (--shred.cooldown > 0) {
                continue;
            }
            shred.cooldown = shred.intervalTicks;
            pulse(player, shred);
        }
    }

    private static void pulse(ServerPlayer player, Shred shred) {
        if (player.isCreative() || player.isSpectator()) {
            // god-mode: pulso sin efecto posible
            if (++shred.failStreak >= MAX_FAIL_STREAK) {
                cancel(shred.target, "no_effect");
            }
            return;
        }
        int before = countTotems(player);
        if (CataclysmConfig.COMMON.shredAutoRefill.get()) {
            refillOffhand(player);
        }
        float healthBefore = player.getHealth();
        // cinturon: ventana de invulnerabilidad fuera ANTES de cada golpe
        player.invulnerableTime = 0;
        player.hurt(ModDamageTypes.source(player.level(), ModDamageTypes.EXECUTION), KILL_DAMAGE);
        int after = countTotems(player);

        if (after < before) {
            // POP: el totem salvo — el espectaculo
            shred.popped++;
            shred.budgetLeft--;
            shred.failStreak = 0;
            Cataclysm.LOGGER.info("[cataclysm] totem pop target={} n={} left={}",
                    player.getGameProfile().getName(), shred.popped, after);
            FxDirector.fire(player.serverLevel(), FxEvent.TOTEM_POP, player.position(), 1.0F);
            FxDirector.sound(player.serverLevel(), player.position(),
                    ModSounds.TOTEM_POP.get(), 1.2F, 0.9F + player.getRandom().nextFloat() * 0.3F);
            TitleDirector.actionbar(player, Component.translatable("cataclysm.shred.counter", after));
            if (shred.budgetLeft <= 0) {
                cancel(shred.target, "budget_exhausted");
            }
        } else if (!player.isAlive()) {
            // sin totem: el golpe fue letal de verdad — clímax; los hooks limpian
            cancel(shred.target, "target_died");
        } else if (player.getHealth() >= healthBefore) {
            // ni pop, ni muerte, ni dano: god-mode de plugin — cortar, no spamear
            if (++shred.failStreak >= MAX_FAIL_STREAK) {
                cancel(shred.target, "no_effect");
            }
        } else {
            shred.failStreak = 0; // hizo dano normal (sin totem en mano pero sobrevivio)
        }
    }

    /** Cuenta totems en TODO el inventario (mochila + hotbar + offhand). */
    public static int countTotems(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /**
     * Los totems NO se apilan (maxStackSize=1) y SOLO salvan desde la mano:
     * antes de cada golpe se sube uno del inventario a la offhand, o la
     * cadena muere en el primer pop.
     */
    public static void refillOffhand(ServerPlayer player) {
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                ItemStack displaced = player.getOffhandItem();
                inv.items.set(i, displaced);
                player.setItemSlot(EquipmentSlot.OFFHAND, stack);
                player.inventoryMenu.broadcastChanges();
                return;
            }
        }
    }

    private TotemShredder() {
    }
}
