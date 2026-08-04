package com.vvrgs.cataclysm.compat;

import com.vvrgs.cataclysm.Cataclysm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Puente OPCIONAL a AAA Particles (Effekseer 1.70e en Minecraft).
 *
 * 100% reflexion — patron probado: si aaa_particles no esta instalado, si la
 * API no coincide o si CUALQUIER invocacion lanza, el puente se
 * auto-desactiva con UN log y el mod sigue perfecto con sus FX nativos.
 * Jamas hard-depend, jamas crashear.
 *
 * API real (aaa-particles-forge 1.20.1-1.4.x):
 *   mod.chloeprime.aaaparticles.api.common.AAALevel
 *       .addParticle(Level, boolean force, ParticleEmitterInfo)  [estatico]
 *   mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo
 *       ctor(ResourceLocation) + encadenables .position(x,y,z) .scale(f)
 *       .rotation(rx,ry,rz) [radianes] — los opcionales se resuelven con
 *       reflexion TOLERANTE: si faltan, se pierde ESA feature, no el puente.
 */
public final class AAABridge {

    private static boolean initialized;
    private static boolean available;

    private static Method addParticle;
    private static Constructor<?> infoCtor;
    @Nullable
    private static Method position;
    @Nullable
    private static Method scale;
    @Nullable
    private static Method rotationLocal;

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!ModList.get().isLoaded("aaa_particles")) {
            Cataclysm.LOGGER.info("[cataclysm] AAA Particles no instalado: FX nativos solamente");
            return;
        }
        try {
            Class<?> aaaLevel = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> infoClass = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            infoCtor = infoClass.getConstructor(ResourceLocation.class);
            addParticle = aaaLevel.getMethod("addParticle", Level.class, boolean.class, infoClass);
            position = tolerant(infoClass, "position", double.class, double.class, double.class);
            scale = tolerant(infoClass, "scale", float.class);
            // API real 1.20.1-1.4.x: rotation(float,float,float) en radianes;
            // rotationLocal solo como fallback de versiones que lo tengan
            rotationLocal = tolerant(infoClass, "rotation", float.class, float.class, float.class);
            if (rotationLocal == null) {
                rotationLocal = tolerant(infoClass, "rotationLocal", float.class, float.class, float.class);
            }
            available = true;
            Cataclysm.LOGGER.info("[cataclysm] puente AAA Particles/Effekseer ACTIVO");
        } catch (Throwable t) {
            available = false;
            Cataclysm.LOGGER.warn("[cataclysm] puente AAA Particles auto-desactivado (API no coincide): {}",
                    t.toString());
        }
    }

    @Nullable
    private static Method tolerant(Class<?> owner, String name, Class<?>... args) {
        try {
            return owner.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            Cataclysm.LOGGER.info("[cataclysm] AAA Particles sin {}(): se pierde esa feature, no el puente", name);
            return null;
        }
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    /**
     * Lanza un effek en el punto. @return false si el puente esta caido
     * (el llamador ya tiene sus FX nativos completos — los effeks ELEVAN).
     */
    public static boolean play(Level level, ResourceLocation effek,
                               double x, double y, double z, float effectScale, float yawDegrees) {
        init();
        if (!available) {
            return false;
        }
        try {
            // instancia NUEVA por lanzamiento: jamas mutar una compartida
            Object info = infoCtor.newInstance(effek);
            if (position != null) {
                position.invoke(info, x, y, z);
            }
            if (scale != null && effectScale != 1.0F) {
                scale.invoke(info, effectScale);
            }
            if (rotationLocal != null && yawDegrees != 0.0F) {
                rotationLocal.invoke(info, 0.0F, (float) Math.toRadians(yawDegrees), 0.0F);
            }
            addParticle.invoke(null, level, true, info);
            return true;
        } catch (Throwable t) {
            available = false;
            Cataclysm.LOGGER.warn("[cataclysm] puente AAA Particles auto-desactivado (invocacion fallo): {}",
                    t.toString());
            return false;
        }
    }

    private AAABridge() {
    }
}
