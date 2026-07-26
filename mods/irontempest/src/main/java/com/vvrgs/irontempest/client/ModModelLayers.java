package com.vvrgs.irontempest.client;

import com.vvrgs.irontempest.IronTempest;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public final class ModModelLayers {
    public static final ModelLayerLocation WAR_TANK =
            new ModelLayerLocation(new ResourceLocation(IronTempest.MODID, "war_tank"), "main");
    public static final ModelLayerLocation TANK_SHELL =
            new ModelLayerLocation(new ResourceLocation(IronTempest.MODID, "tank_shell"), "main");
    public static final ModelLayerLocation CRUISE_MISSILE =
            new ModelLayerLocation(new ResourceLocation(IronTempest.MODID, "cruise_missile"), "main");
    public static final ModelLayerLocation MLRS_ROCKET =
            new ModelLayerLocation(new ResourceLocation(IronTempest.MODID, "mlrs_rocket"), "main");
    public static final ModelLayerLocation WARSHIP =
            new ModelLayerLocation(new ResourceLocation(IronTempest.MODID, "warship"), "main");

    private ModModelLayers() {}
}
