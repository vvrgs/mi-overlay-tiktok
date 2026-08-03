package com.vvrgs.irontempest.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Config COMMON: controla daño, destrucción de terreno y FX pesados. */
public final class WarConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue DOT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue KNOCKBACK_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue SUSTAINED_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue LETHAL_STRIKES;
    public static final ForgeConfigSpec.BooleanValue TERRAIN_DESTRUCTION;
    public static final ForgeConfigSpec.IntValue GLOBAL_BLOCK_BUDGET_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue COSMETIC_SCORCH;
    public static final ForgeConfigSpec.BooleanValue LEAVE_FIRE;
    public static final ForgeConfigSpec.IntValue MAX_CRUISE_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue ROCKET_MAX_PENDING;
    public static final ForgeConfigSpec.IntValue ROCKETS_PER_SALVO;
    public static final ForgeConfigSpec.BooleanValue POST_SHADER;
    public static final ForgeConfigSpec.BooleanValue FLASH_OVERLAY;
    public static final ForgeConfigSpec.BooleanValue BROADCAST_MESSAGES;
    public static final ForgeConfigSpec.BooleanValue STREAMER_MODE;
    public static final ForgeConfigSpec.BooleanValue TOTEM_SHREDDER;
    public static final ForgeConfigSpec.IntValue SHRED_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue SHRED_AUTO_REFILL;
    public static final ForgeConfigSpec.DoubleValue SHRED_BUDGET_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue EXECUTION_MAX_POPS;
    public static final ForgeConfigSpec.DoubleValue FX_DENSITY;
    public static final ForgeConfigSpec.BooleanValue FOLLOW_ACROSS_DIMENSIONS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("damage");
        DAMAGE_MULTIPLIER = b.comment("Multiplicador global de daño (0 = inofensivo, solo espectáculo). OJO: con lethalStrikes=true cualquier valor > 0 mata igual salvo tótem; el multiplicador solo escala el daño radial.")
                .defineInRange("damageMultiplier", 1.0D, 0.0D, 10.0D);
        DOT_MULTIPLIER = b.comment("Multiplicador del daño SOSTENIDO (zonas ardientes y aflicciones post-impacto).")
                .defineInRange("dotMultiplier", 1.0D, 0.0D, 10.0D);
        KNOCKBACK_STRENGTH = b.comment("Fuerza del empujón físico de las ondas expansivas (0 = sin empujón).")
                .defineInRange("knockbackStrength", 1.0D, 0.0D, 5.0D);
        SUSTAINED_DAMAGE = b.comment("Daño sostenido tras cada impacto: el cráter arde 3-8 s y quema a quien lo pise; los alcanzados siguen recibiendo daño.")
                .define("sustainedDamage", true);
        LETHAL_STRIKES = b.comment("Si true, los impactos directos matan salvo tótem (killIfNoTotem). Si false, solo daño radial normal.")
                .define("lethalStrikes", true);
        b.pop();

        b.push("terrain");
        TERRAIN_DESTRUCTION = b.comment("Destrucción de terreno (cráteres). Siempre amortizada por tick y con guardas de seguridad.")
                .define("terrainDestruction", true);
        GLOBAL_BLOCK_BUDGET_PER_TICK = b.comment("Presupuesto GLOBAL de bloques destruidos por tick en todo el servidor (amortización).")
                .defineInRange("globalBlockBudgetPerTick", 800, 16, 20000);
        COSMETIC_SCORCH = b.comment("Quemaduras cosméticas pequeñas (≤5 bloques) para el tier spameable (rocketrain).")
                .define("cosmeticScorch", true);
        LEAVE_FIRE = b.comment("Dejar fuego en los cráteres.")
                .define("leaveFire", true);
        b.pop();

        b.push("concurrency");
        MAX_CRUISE_PER_PLAYER = b.comment("Máximo de misiles crucero simultáneos por jugador (tier M). El resto se encola FIFO.")
                .defineInRange("maxCruisePerPlayer", 5, 1, 10);
        ROCKET_MAX_PENDING = b.comment("Cola máxima de cohetes pendientes por jugador (tier S, anti-spam de 100 regalos de golpe).")
                .defineInRange("rocketMaxPending", 300, 10, 2000);
        ROCKETS_PER_SALVO = b.comment("Cohetes que añade cada comando rocketrain a la cola.")
                .defineInRange("rocketsPerSalvo", 6, 1, 30);
        b.pop();

        b.push("clientFx");
        FX_DENSITY = b.comment("Densidad de las partículas del mod (0.25-2.0). Bájalo si tus otros mods ya llenan la pantalla y quieres que Iron Tempest se lea limpio.")
                .defineInRange("fxDensity", 1.0D, 0.25D, 2.0D);
        POST_SHADER = b.comment("Post-shader de distorsión de onda de choque (se desactiva solo si Oculus/Iris está presente).")
                .define("postShader", true);
        FLASH_OVERLAY = b.comment("Flash de pantalla breve en explosiones grandes.")
                .define("flashOverlay", true);
        b.pop();

        b.push("streamer");
        STREAMER_MODE = b.comment("MODO STREAMER: pensado para lives donde el objetivo es MATARTE con espectáculo. Activa: cráteres grandes en TODO (los cohetes tier S incluidos), presupuesto de bloques 2200/tick, DoT perforante (ignora armadura+Protection) y trituradora de tótems en los ataques.")
                .define("streamerMode", true);
        TOTEM_SHREDDER = b.comment("Trituradora de tótems: los impactos directos rompen tótems EN CADENA saltándose la ventana de invulnerabilidad (requiere lethalStrikes=true y streamerMode).")
                .define("totemShredder", true);
        SHRED_INTERVAL_TICKS = b.comment("Ticks entre pops de tótem (4 = 5 pops/segundo). No bajar de 2: la animación deja de leerse.")
                .defineInRange("shredIntervalTicks", 4, 1, 20);
        FOLLOW_ACROSS_DIMENSIONS = b.comment("Los ataques te PERSIGUEN cuando otros plugins te teletransportan (incluso al End/Nether): el tanque re-cae en drop-pod y la nave re-warpea contigo.")
                .define("followAcrossDimensions", true);
        SHRED_AUTO_REFILL = b.comment("Auto-recarga la offhand con tótems del inventario antes de cada pulso (los tótems NO se apilan y solo salvan desde la mano — sin esto la cadena muere en el primer pop).")
                .define("shredAutoRefill", true);
        SHRED_BUDGET_MULTIPLIER = b.comment("Multiplicador del presupuesto de pops por ataque.")
                .defineInRange("shredBudgetMultiplier", 1.0D, 0.0D, 5.0D);
        EXECUTION_MAX_POPS = b.comment("Tótems máximos que devora el comando execution.")
                .defineInRange("executionMaxPops", 40, 1, 150);
        b.pop();

        b.push("feedback");
        BROADCAST_MESSAGES = b.comment("Mensajes de chat al lanzar/encolar ataques.")
                .define("broadcastMessages", true);
        b.pop();

        SPEC = b.build();
    }

    // ------------------------------------------------------------ helpers streamer
    /** Presupuesto de bloques/tick efectivo (streamer sube el suelo a 2200). */
    public static int effectiveBlockBudget() {
        int base = GLOBAL_BLOCK_BUDGET_PER_TICK.get();
        return STREAMER_MODE.get() ? Math.max(base, 2200) : base;
    }

    /** Radio de cráter efectivo: streamer agranda (3→4, 4→6, 2→3, 5→7). */
    public static int craterRadius(int base) {
        if (!STREAMER_MODE.get()) {
            return base;
        }
        return base + (base >= 4 ? 2 : 1);
    }

    /** Damage type de los pulsos DoT: napalm perforante en streamer. */
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> sustainedType(
            net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> fallback) {
        return STREAMER_MODE.get() ? com.vvrgs.irontempest.registry.ModDamage.NAPALM : fallback;
    }

    public static boolean shredEnabled() {
        return STREAMER_MODE.get() && TOTEM_SHREDDER.get();
    }

    private WarConfig() {}
}
