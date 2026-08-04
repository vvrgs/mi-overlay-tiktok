package com.vvrgs.cataclysm;

import com.vvrgs.cataclysm.network.CataclysmNetwork;
import com.vvrgs.cataclysm.registry.ModEntities;
import com.vvrgs.cataclysm.registry.ModParticles;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Cataclismo: desastres naturales y eventos cosmicos dirigidos a un jugador,
 * disparados por comandos de consola (regalos de TikTok Live).
 *
 * Server de referencia: Mohist 1.20.1 (hibrido Forge+Bukkit) — todo el motor
 * es defensivo: nunca asume estado limpio, nunca deja ticks huerfanos.
 */
@Mod(Cataclysm.MODID)
public final class Cataclysm {

    public static final String MODID = "cataclysm";
    public static final Logger LOGGER = LogManager.getLogger("cataclysm");

    public Cataclysm() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModParticles.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModEntities.REGISTER.register(modBus);

        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CataclysmConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CataclysmConfig.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CataclysmNetwork::register);
    }
}
