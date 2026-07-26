package com.vvrgs.irontempest;

import com.mojang.logging.LogUtils;
import com.vvrgs.irontempest.config.WarConfig;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModEntities;
import com.vvrgs.irontempest.registry.ModParticles;
import com.vvrgs.irontempest.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(IronTempest.MODID)
public final class IronTempest {
    public static final String MODID = "irontempest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IronTempest() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModParticles.REGISTER.register(modBus);

        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WarConfig.SPEC);

        LOGGER.info("[irontempest] Iron Tempest cargado — arsenal listo");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
