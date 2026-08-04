package com.vvrgs.cataclysm;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Config TOML por secciones. Defaults pensados para lives de TikTok donde el
 * objetivo de los viewers es matar al streamer (streamer mode ON).
 */
public final class CataclysmConfig {

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private CataclysmConfig() {
    }

    public static final class Common {
        // damage
        public final ForgeConfigSpec.DoubleValue damageMultiplier;
        public final ForgeConfigSpec.DoubleValue dotMultiplier;
        public final ForgeConfigSpec.DoubleValue knockbackStrength;
        public final ForgeConfigSpec.DoubleValue windStrength;
        public final ForgeConfigSpec.BooleanValue sustainedDamage;
        public final ForgeConfigSpec.BooleanValue lethalStrikes;
        // terrain
        public final ForgeConfigSpec.BooleanValue terrainDestruction;
        public final ForgeConfigSpec.IntValue globalBlockBudgetPerTick;
        public final ForgeConfigSpec.IntValue maxCraterRadius;
        public final ForgeConfigSpec.IntValue maxFissureDepth;
        public final ForgeConfigSpec.IntValue maxQueuedBlockOps;
        // concurrency
        public final ForgeConfigSpec.IntValue maxTierMPerPlayer;
        public final ForgeConfigSpec.IntValue maxTierCPerPlayer;
        public final ForgeConfigSpec.IntValue maxPendingPerPlayer;
        public final ForgeConfigSpec.IntValue maxTierSQueue;
        // streamer
        public final ForgeConfigSpec.BooleanValue streamerMode;
        public final ForgeConfigSpec.BooleanValue totemShredder;
        public final ForgeConfigSpec.IntValue shredIntervalTicks;
        public final ForgeConfigSpec.BooleanValue shredAutoRefill;
        public final ForgeConfigSpec.DoubleValue shredBudgetMultiplier;
        public final ForgeConfigSpec.BooleanValue followAcrossDimensions;
        // feedback
        public final ForgeConfigSpec.BooleanValue broadcastMessages;

        Common(ForgeConfigSpec.Builder b) {
            b.comment("Dano directo y sostenido").push("damage");
            damageMultiplier = b
                    .comment("Multiplicador global del dano directo de los desastres.")
                    .defineInRange("damageMultiplier", 1.0D, 0.0D, 100.0D);
            dotMultiplier = b
                    .comment("Multiplicador del dano sostenido (napalm, ceniza, zonas ardientes).")
                    .defineInRange("dotMultiplier", 1.0D, 0.0D, 100.0D);
            knockbackStrength = b
                    .comment("Fuerza de las ondas expansivas (impulso fisico real).")
                    .defineInRange("knockbackStrength", 1.0D, 0.0D, 10.0D);
            windStrength = b
                    .comment("Fuerza del viento sostenido (huracan, tornado, tormenta).")
                    .defineInRange("windStrength", 1.0D, 0.0D, 10.0D);
            sustainedDamage = b
                    .comment("Activa las aflicciones de dano sostenido que persiguen al jugador.")
                    .define("sustainedDamage", true);
            lethalStrikes = b
                    .comment("Activa los golpes letales killIfNoTotem (10000 de dano que el totem SI salva).")
                    .define("lethalStrikes", true);
            b.pop();

            b.comment("Destruccion de terreno (siempre amortizada por tick)").push("terrain");
            terrainDestruction = b
                    .comment("Permite crateres, fisuras, conos volcanicos y agua de tsunami reales.")
                    .define("terrainDestruction", true);
            globalBlockBudgetPerTick = b
                    .comment("Presupuesto GLOBAL de operaciones de bloque por tick. Un tick jamas se congela por terreno.")
                    .defineInRange("globalBlockBudgetPerTick", 2000, 50, 20000);
            maxCraterRadius = b
                    .comment("Cap duro de radio de crater.")
                    .defineInRange("maxCraterRadius", 16, 2, 48);
            maxFissureDepth = b
                    .comment("Cap duro de profundidad de fisura del terremoto.")
                    .defineInRange("maxFissureDepth", 30, 5, 64);
            maxQueuedBlockOps = b
                    .comment("Cap de la cola global de terreno; por encima se descartan ops (con log).")
                    .defineInRange("maxQueuedBlockOps", 250000, 1000, 2000000);
            b.pop();

            b.comment("Cupos de concurrencia por tier").push("concurrency");
            maxTierMPerPlayer = b
                    .comment("Sesiones tier M (solapables) simultaneas por jugador; el resto encola.")
                    .defineInRange("maxTierMPerPlayer", 5, 1, 16);
            maxTierCPerPlayer = b
                    .comment("Sesiones tier C (cinematicas) simultaneas por jugador.")
                    .defineInRange("maxTierCPerPlayer", 2, 1, 4);
            maxPendingPerPlayer = b
                    .comment("Cola de ataques pendientes por jugador (tier M/C/U); llena => feedback queue_full.")
                    .defineInRange("maxPendingPerPlayer", 10, 1, 64);
            maxTierSQueue = b
                    .comment("Cap de la cola spameable por jugador (meteoros/rayos pendientes).")
                    .defineInRange("maxTierSQueue", 300, 10, 2000);
            b.pop();

            b.comment("Modo streamer: totems reventando en cadena, persecucion entre dimensiones").push("streamer");
            streamerMode = b
                    .comment("Perforacion total de armadura/encantamientos en dano sostenido y cadencias agresivas.")
                    .define("streamerMode", true);
            totemShredder = b
                    .comment("Activa la trituradora de totems (pops en cadena; el totem SIEMPRE puede salvar).")
                    .define("totemShredder", true);
            shredIntervalTicks = b
                    .comment("Ticks entre pulsos de trituradora. 4 = 5 pops/s. Minimo 2.")
                    .defineInRange("shredIntervalTicks", 4, 2, 40);
            shredAutoRefill = b
                    .comment("Auto-recarga la offhand con un totem del inventario antes de cada golpe (los totems no se apilan y solo salvan desde la mano).")
                    .define("shredAutoRefill", true);
            shredBudgetMultiplier = b
                    .comment("Multiplicador del presupuesto de pops de cada desastre.")
                    .defineInRange("shredBudgetMultiplier", 1.0D, 0.0D, 10.0D);
            followAcrossDimensions = b
                    .comment("Los desastres persiguen al jugador incluso al cambiar de dimension (plugins que teletransportan).")
                    .define("followAcrossDimensions", true);
            b.pop();

            b.comment("Mensajes").push("feedback");
            broadcastMessages = b
                    .comment("Anuncios globales de los desastres tier U (sirena + titulo a todo el server).")
                    .define("broadcastMessages", true);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue postShader;
        public final ForgeConfigSpec.DoubleValue fxDensity;
        public final ForgeConfigSpec.BooleanValue flashOverlay;
        public final ForgeConfigSpec.BooleanValue screenShake;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("FX de cliente").push("clientFx");
            postShader = b
                    .comment("Post-shaders propios (distorsion, heat-haze, aberracion). Se auto-desactivan si Iris/Oculus esta cargado.")
                    .define("postShader", true);
            fxDensity = b
                    .comment("Multiplicador de densidad de particulas de TODOS los FX del mod.")
                    .defineInRange("fxDensity", 1.0D, 0.25D, 2.0D);
            flashOverlay = b
                    .comment("Flash de pantalla en impactos (con falloff por distancia).")
                    .define("flashOverlay", true);
            screenShake = b
                    .comment("Screen shake (modelo trauma^2).")
                    .define("screenShake", true);
            b.pop();
        }
    }
}
