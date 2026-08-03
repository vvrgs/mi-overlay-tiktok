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
        POST_SHADER = b.comment("Post-shader de distorsión de onda de choque (se desactiva solo si Oculus/Iris está presente).")
                .define("postShader", true);
        FLASH_OVERLAY = b.comment("Flash de pantalla breve en explosiones grandes.")
                .define("flashOverlay", true);
        b.pop();

        b.push("feedback");
        BROADCAST_MESSAGES = b.comment("Mensajes de chat al lanzar/encolar ataques.")
                .define("broadcastMessages", true);
        b.pop();

        SPEC = b.build();
    }

    private WarConfig() {}
}
