package com.vvrgs.irontempest.client.sound;

import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.entity.CruiseMissileEntity;
import com.vvrgs.irontempest.entity.MlrsRocketEntity;
import com.vvrgs.irontempest.entity.TankEntity;
import com.vvrgs.irontempest.entity.WarshipEntity;
import com.vvrgs.irontempest.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Arranca los loops de sonido en cuanto la entidad aparece en el mundo cliente. */
@Mod.EventBusSubscriber(modid = IronTempest.MODID, value = Dist.CLIENT)
public final class ClientSoundHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() instanceof TankEntity tank) {
            mc.getSoundManager().play(new TankEngineSound(tank));
        } else if (event.getEntity() instanceof CruiseMissileEntity missile) {
            mc.getSoundManager().play(new MissileLoopSound(missile));
        } else if (event.getEntity() instanceof MlrsRocketEntity rocket) {
            mc.getSoundManager().play(new EntityBoundSoundInstance(
                    ModSounds.SHELL_WHISTLE.get(), SoundSource.HOSTILE, 1.0F, 1.0F,
                    rocket, rocket.getId()));
        } else if (event.getEntity() instanceof WarshipEntity ship) {
            // BeamHumSound se auto-gestiona: mudo hasta la fase BEAM.
            mc.getSoundManager().play(new BeamHumSound(ship));
        }
    }

    private ClientSoundHandler() {}
}
