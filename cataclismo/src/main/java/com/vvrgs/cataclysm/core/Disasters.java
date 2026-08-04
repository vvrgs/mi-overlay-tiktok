package com.vvrgs.cataclysm.core;

import com.vvrgs.cataclysm.disasters.ApocalypseSession;
import com.vvrgs.cataclysm.disasters.EarthquakeSession;
import com.vvrgs.cataclysm.disasters.FirestormSession;
import com.vvrgs.cataclysm.disasters.HurricaneSession;
import com.vvrgs.cataclysm.disasters.LightningQueueSession;
import com.vvrgs.cataclysm.disasters.MeteorRainSession;
import com.vvrgs.cataclysm.disasters.NaturalExecutionSession;
import com.vvrgs.cataclysm.disasters.PlanetaryImpactSession;
import com.vvrgs.cataclysm.disasters.ThunderstormSession;
import com.vvrgs.cataclysm.disasters.TornadoSession;
import com.vvrgs.cataclysm.disasters.TsunamiSession;
import com.vvrgs.cataclysm.disasters.VolcanoSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Catalogo de desastres: nombre de comando (espanol, sin mayusculas ni
 * tildes), tier de concurrencia y factory de sesion.
 */
public enum Disasters {

    METEOROS("meteoros", Tier.S, MeteorRainSession::new),
    RAYO("rayo", Tier.S, LightningQueueSession::new),
    TORMENTA("tormenta", Tier.M, ThunderstormSession::new),
    FUEGO("fuego", Tier.M, FirestormSession::new),
    TORNADO("tornado", Tier.C, TornadoSession::new),
    HURACAN("huracan", Tier.C, HurricaneSession::new),
    TERREMOTO("terremoto", Tier.C, EarthquakeSession::new),
    VOLCAN("volcan", Tier.C, VolcanoSession::new),
    TSUNAMI("tsunami", Tier.C, TsunamiSession::new),
    IMPACTO("impacto", Tier.U, PlanetaryImpactSession::new),
    APOCALIPSIS("apocalipsis", Tier.U, ApocalypseSession::new),
    EJECUCION_NATURAL("ejecucion_natural", Tier.U, NaturalExecutionSession::new);

    @FunctionalInterface
    public interface Factory {
        DisasterSession create(MinecraftServer server, ServerPlayer target);
    }

    private final String commandName;
    private final Tier tier;
    private final Factory factory;

    Disasters(String commandName, Tier tier, Factory factory) {
        this.commandName = commandName;
        this.tier = tier;
        this.factory = factory;
    }

    public String commandName() {
        return commandName;
    }

    public Tier tier() {
        return tier;
    }

    public DisasterSession create(MinecraftServer server, ServerPlayer target) {
        return factory.create(server, target);
    }

    /** Claves de lang: cataclysm.attack.<comando>.title / .subtitle / .bossbar */
    public String titleKey() {
        return "cataclysm.attack." + commandName + ".title";
    }

    public String subtitleKey() {
        return "cataclysm.attack." + commandName + ".subtitle";
    }

    public String bossbarKey() {
        return "cataclysm.attack." + commandName + ".bossbar";
    }
}
